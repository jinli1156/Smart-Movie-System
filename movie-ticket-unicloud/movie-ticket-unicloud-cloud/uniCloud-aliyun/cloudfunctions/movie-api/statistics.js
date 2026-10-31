'use strict'

const { ApiError, assert, success, numericId, requireUser } = require('./core')
const { scopeCinemaId, requireCinema, cinemaShows, orderFilter } = require('./permissions')

async function all(db, collection, filter = {}) {
  const rows = []
  for (let page = 0; page < 100; page++) {
    let query = db.collection(collection)
    if (Object.keys(filter).length) query = query.where(filter)
    const result = await query.skip(page * 1000).limit(1000).get()
    rows.push(...(result.data || []))
    if ((result.data || []).length < 1000) return rows
  }
  throw new ApiError(503, '数据量过大，请缩小统计范围')
}

function paid(order) { return order.status === 1 && !!order.payTime }
function dateOf(value) { return String(value || '').slice(0, 10) }
function sumMoney(rows) { return Math.round(rows.reduce((sum, item) => sum + Number(item.totalPrice || 0), 0) * 100) / 100 }

async function ranking(db, orders, limit) {
  const showtimes = await all(db, 'mts_showtime')
  const movies = await all(db, 'mts_movie')
  const byShowtime = new Map(showtimes.map(item => [item.id, item.movieId]))
  const names = new Map(movies.map(item => [item.id, item.title]))
  const totals = new Map()
  for (const order of orders.filter(paid)) {
    const id = byShowtime.get(order.showtimeId)
    if (!id) continue
    const value = totals.get(id) || { movieId: id, movieTitle: names.get(id) || '', boxOffice: 0, ticketCount: 0 }
    value.boxOffice += Number(order.totalPrice || 0)
    value.ticketCount += Number(order.seatCount || 0)
    totals.set(id, value)
  }
  return [...totals.values()].map(item => ({ ...item, boxOffice: Math.round(item.boxOffice * 100) / 100 })).sort((a, b) => b.boxOffice - a.boxOffice).slice(0, limit)
}

async function handle(request, db) {
  const { method, path, query } = request
  if (path.startsWith('/statistics/')) requireUser(request, ['ADMIN', 'CINEMA_ADMIN'])
  const scope = path.startsWith('/statistics/') ? scopeCinemaId(request, null) : null
  assert(method === 'GET', 405, '请求方法无效')
  if (path === '/statistics/data-counts') {
    requireUser(request, ['ADMIN'])
    const names = [
      'mts_user', 'mts_cinema', 'mts_hall', 'mts_movie', 'mts_showtime', 'mts_order',
      'mts_user_coupon', 'mts_comment', 'mts_behavior', 'mts_coupon_code',
      'mts_coupon_redemption', 'mts_assistant_conversation', 'mts_assistant_turn',
      'mts_assistant_preference', 'mts_assistant_checkout', 'mts_scheduling_draft',
      'mts_seat_hold', 'mts_counter'
    ]
    const counts = await Promise.all(names.map(name => db.collection(name).count()))
    return success(Object.fromEntries(names.map((name, index) => [name, counts[index].total])))
  }
  if (path === '/statistics/dashboard') {
    if (scope != null) {
      const shows = await cinemaShows(db, scope)
      const orders = await all(db, 'mts_order', await orderFilter(request, db))
      const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' })
      return success({ movieCount: new Set(shows.map(item => item.movieId)).size, cinemaCount: 1,
        userCount: new Set(orders.map(item => item.userId)).size,
        todayOrderCount: orders.filter(item => dateOf(item.createTime) === today).length,
        todayRevenue: sumMoney(orders.filter(item => paid(item) && dateOf(item.payTime) === today)) })
    }
    const [movieCount, cinemaCount, userCount, orders] = await Promise.all([
      db.collection('mts_movie').count(), db.collection('mts_cinema').count(), db.collection('mts_user').count(), all(db, 'mts_order')
    ])
    const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' })
    return success({ movieCount: movieCount.total, cinemaCount: cinemaCount.total, userCount: userCount.total, todayOrderCount: orders.filter(item => dateOf(item.createTime) === today).length, todayRevenue: sumMoney(orders.filter(item => paid(item) && dateOf(item.payTime) === today)) })
  }
  if (path === '/statistics/box-office' || path === '/movie/box-office/history' || path === '/movie/box-office/today') {
    const limit = Math.max(1, Math.min(100, Number(query.limit) || 10))
    const orders = await all(db, 'mts_order', scope == null ? {} : await orderFilter(request, db))
    const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' })
    return success(await ranking(db, path.endsWith('today') ? orders.filter(item => dateOf(item.payTime) === today) : orders, limit))
  }
  const insight = /^\/movie\/(\d+)\/insight$/.exec(path)
  if (insight) {
    const movieId = numericId(insight[1])
    const movie = (await db.collection('mts_movie').where({ id: movieId }).limit(1).get()).data?.[0]
    assert(movie, 404, '电影不存在')
    const today = new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' })
    const showtimes = await all(db, 'mts_showtime', { movieId })
    const todayShows = showtimes.filter(item => dateOf(item.startTime) === today)
    const totalSeats = todayShows.reduce((sum, item) => sum + Number(item.totalSeats || 0), 0)
    const ids = new Set(showtimes.map(item => item.id))
    const orders = (await all(db, 'mts_order')).filter(item => paid(item) && ids.has(item.showtimeId))
    const todayOrders = orders.filter(item => dateOf(item.payTime) === today)
    const start = Date.parse(`${today}T00:00:00Z`) - 6 * 86400000
    const trendPoints = Array.from({ length: 7 }, (_, index) => {
      const day = new Date(start + index * 86400000).toISOString().slice(0, 10)
      return orders.filter(item => dateOf(item.payTime) === day).reduce((sum, item) => sum + Number(item.seatCount || 0), 0)
    })
    const todayViewerCount = trendPoints[6]
    const yesterdayCount = trendPoints[5]
    const trendPercent = yesterdayCount ? Math.max(-999, Math.min(999, Math.round((todayViewerCount - yesterdayCount) * 100 / yesterdayCount))) : todayViewerCount ? 100 : 0
    const soldSeats = orders.filter(item => todayShows.some(show => show.id === item.showtimeId)).reduce((sum, item) => sum + Number(item.seatCount || 0), 0)
    return success({ movieId, todayBoxOffice: sumMoney(todayOrders), todayViewerCount, occupancyRate: totalSeats ? Math.round(soldSeats * 1000 / totalSeats) / 10 : 0, trendPercent, trendPoints, generatedAt: new Date(Date.now() + 8 * 3600000).toISOString().replace('Z', '+08:00') })
  }
  if (path === '/statistics/revenue') {
    const start = String(query.startDate || '')
    const end = String(query.endDate || '')
    const first = Date.parse(`${start}T00:00:00Z`)
    const last = Date.parse(`${end}T00:00:00Z`)
    assert(Number.isFinite(first) && Number.isFinite(last) && last >= first && last - first <= 366 * 86400000, 400, '统计日期范围无效')
    const orders = await all(db, 'mts_order', await orderFilter(request, db))
    const totals = new Map()
    for (const order of orders.filter(paid)) {
      const day = dateOf(order.payTime)
      if (day < start || day > end) continue
      const value = totals.get(day) || { date: day, revenue: 0, orderCount: 0 }
      value.revenue += Number(order.totalPrice || 0)
      value.orderCount++
      totals.set(day, value)
    }
    const result = []
    for (let day = first; day <= last; day += 86400000) {
      const date = new Date(day).toISOString().slice(0, 10)
      const value = totals.get(date) || { date, revenue: 0, orderCount: 0 }
      result.push({ ...value, revenue: Math.round(value.revenue * 100) / 100 })
    }
    return success(result)
  }
  const showtimeMatch = /^\/statistics\/showtime\/(\d+)$/.exec(path)
  if (showtimeMatch) {
    const showtimeId = numericId(showtimeMatch[1])
    const showtime = (await db.collection('mts_showtime').where({ id: showtimeId }).limit(1).get()).data?.[0]
    if (!showtime) return success({})
    requireCinema(request, showtime.cinemaId)
    const orders = await all(db, 'mts_order', { showtimeId })
    const current = orders.filter(paid)
    const soldSeats = current.reduce((sum, item) => sum + Number(item.seatCount || 0), 0)
    const totalSeats = Number(showtime.totalSeats || 0)
    return success({ totalSeats, availableSeats: Math.max(0, totalSeats - soldSeats), soldSeats, occupancyRate: totalSeats ? (soldSeats / totalSeats * 100).toFixed(2) : '0.00', revenue: sumMoney(current) })
  }
  throw new ApiError(404, '接口尚未迁移')
}

module.exports = { handle }
