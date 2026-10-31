'use strict'

const { ApiError, assert, success, numericId, now, requireUser } = require('./core')

function bool(value, fallback = true) { return value === undefined ? fallback : value === true || value === 'true' }
function cents(value) { return Math.round(Number(value) * 100) }
function yuan(value) { return Math.round(value) / 100 }

async function discounts(db, userId) {
  if (!userId) return { points: 0, coupons: [] }
  const user = (await db.collection('mts_user').where({ id: userId }).limit(1).get()).data?.[0]
  assert(user, 401, '当前账号不可用')
  const coupons = (await db.collection('mts_user_coupon').where({ userId, status: 0 }).limit(100).get()).data || []
  return { points: Number(user.points || 0), coupons: coupons.filter(item => Date.parse(item.validFrom) <= Date.now() && Date.parse(item.expireTime) > Date.now()) }
}

function plans(originalPrice, points, coupons, usePoints) {
  const original = cents(originalPrice)
  const result = []
  for (const coupon of [null, ...coupons]) {
    if (coupon && Number(coupon.minSpend) > originalPrice) continue
    const couponCents = Math.min(original, cents(coupon?.amount || 0))
    for (const spendPoints of [0, ...(usePoints ? [Math.floor(Math.min(points, 100, original - couponCents) * 10) / 10] : [])]) {
      if (spendPoints !== 0 && spendPoints < 0.1) continue
      const pointCents = Math.round(spendPoints)
      result.push({ ...(coupon ? { couponId: coupon.id } : {}), couponName: coupon?.name || '不使用优惠券', couponDiscount: yuan(couponCents), pointsUsed: spendPoints, pointsDiscount: yuan(pointCents), discountAmount: yuan(couponCents + pointCents), totalPrice: yuan(original - couponCents - pointCents) })
    }
  }
  return result.sort((a, b) => a.totalPrice - b.totalPrice || a.pointsUsed - b.pointsUsed || Number(!!a.couponId) - Number(!!b.couponId))
}

async function handle(request, db) {
  const { method, path, query } = request
  assert(method === 'GET', 405, '请求方法无效')
  const quote = /^\/pricing\/orders\/([^/]+)\/quote$/.exec(path)
  if (quote) {
    const userId = requireUser(request).id
    const order = (await db.collection('mts_order').where({ orderNo: quote[1], userId }).limit(1).get()).data?.[0]
    assert(order && order.status === 0 && Date.parse(order.createTime) + 10 * 60 * 1000 > Date.now(), 404, '未找到可支付的订单')
    const account = await discounts(db, userId)
    const originalPrice = Number(order.originalPrice || order.totalPrice)
    const options = plans(originalPrice, account.points, account.coupons, bool(query.usePoints))
    return success({ orderNo: order.orderNo, originalPrice, availablePoints: account.points, eligibleCouponCount: account.coupons.filter(item => Number(item.minSpend) <= originalPrice).length, bestPlan: options[0], plans: options.slice(0, 3), notice: '仅试算；支付时重新校验优惠券、积分和订单状态。' })
  }
  if (path !== '/pricing/compare') throw new ApiError(404, '接口不存在')
  const people = Number(query.people)
  assert(Number.isSafeInteger(people) && people >= 1 && people <= 5, 400, '人数须为1至5人')
  assert(!!query.movieId !== !!query.showtimeId, 400, '请提供电影或场次（二选一）')
  const chosen = query.showtimeId ? (await db.collection('mts_showtime').where({ id: numericId(query.showtimeId) }).limit(1).get()).data?.[0] : null
  assert(!query.showtimeId || chosen, 404, '场次不存在')
  const movieId = chosen?.movieId || numericId(query.movieId)
  const movie = (await db.collection('mts_movie').where({ id: movieId, status: 1 }).limit(1).get()).data?.[0]
  assert(movie, 400, '该电影暂不可购票')
  const date = String(query.date || chosen?.startTime?.slice(0, 10) || now().slice(0, 10))
  assert(/^\d{4}-\d{2}-\d{2}$/.test(date), 400, '日期无效')
  const showtimes = (await db.collection('mts_showtime').where({ movieId, status: 0 }).limit(1000).get()).data || []
  const cinemas = (await db.collection('mts_cinema').where({ status: 1 }).limit(1000).get()).data || []
  const halls = (await db.collection('mts_hall').where({ status: 1 }).limit(1000).get()).data || []
  const cinemaMap = new Map(cinemas.map(item => [item.id, item]))
  const hallMap = new Map(halls.map(item => [item.id, item]))
  const account = await discounts(db, request.user?.id)
  const usePoints = bool(query.usePoints)
  const useCoupons = bool(query.useCoupons)
  const holds = showtimes.length ? (await db.collection('mts_seat_hold').where({ showtimeId: db.command.in(showtimes.map(item => item.id)) }).limit(1000).get()).data || [] : []
  const held = new Map()
  for (const item of holds) if (item.status === 'paid' || item.status === 'pending' && item.expiresAt > Date.now()) held.set(item.showtimeId, (held.get(item.showtimeId) || 0) + 1)
  const candidates = showtimes.filter(item => item.startTime?.slice(0, 10) === date && Date.parse(item.startTime) >= Date.now() + 15 * 60 * 1000 && Number(item.totalSeats) - (held.get(item.id) || 0) >= people && cinemaMap.has(item.cinemaId) && hallMap.has(item.hallId))
  const offers = candidates.map(item => {
    const originalPrice = Math.round(Number(item.basePrice) * people * 100) / 100
    return { showtimeId: item.id, cinemaId: item.cinemaId, cinemaName: cinemaMap.get(item.cinemaId).name, hallName: hallMap.get(item.hallId).name, startTime: item.startTime, availableSeats: Number(item.totalSeats) - (held.get(item.id) || 0), basePrice: item.basePrice, originalPrice, bestPlan: plans(originalPrice, account.points, useCoupons ? account.coupons : [], usePoints)[0], reason: '按当前优惠后的实付金额排序' }
  }).filter(item => query.maxTotal === undefined || item.bestPlan.totalPrice <= Number(query.maxTotal))
  offers.sort((a, b) => a.bestPlan.totalPrice - b.bestPlan.totalPrice || a.bestPlan.pointsUsed - b.bestPlan.pointsUsed || a.startTime.localeCompare(b.startTime))
  return success({ movieId, movieTitle: movie.title, moviePoster: movie.poster, date, people, personalized: !!request.user, usePoints, useCoupons, availablePoints: account.points, comparedCount: candidates.length, matchedCount: offers.length, queriedAt: now(), offers: offers.slice(0, 10), notice: '查询不锁座、不占券；支付时重新校验座位和优惠。' })
}

module.exports = { handle, plans, discounts }
