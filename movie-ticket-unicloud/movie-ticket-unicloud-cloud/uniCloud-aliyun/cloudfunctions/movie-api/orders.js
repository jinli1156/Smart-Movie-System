'use strict'

const crypto = require('crypto')
const { ApiError, assert, success, numericId, pageArgs, pageResult, now, clean, nextId, requireUser } = require('./core')
const { requireOrder, orderFilter } = require('./permissions')
const expiry = require('./order-expiry')

const STATUS = { 0: '待支付', 1: '已支付', 2: '已取消', 3: '已退款' }
const HOLD_MS = 10 * 60 * 1000

function millis(value) {
  if (typeof value === 'number') return value
  if (value instanceof Date) return value.getTime()
  const text = String(value || '').replace(' ', 'T')
  return Date.parse(/[Zz]|[+-]\d\d:\d\d$/.test(text) ? text : `${text}+08:00`)
}

async function one(db, collection, filter, message) {
  const result = await db.collection(collection).where(filter).limit(1).get()
  assert(result.data?.length, 404, message)
  return result.data[0]
}

async function withTransaction(db, action) {
  const transaction = await db.startTransaction()
  try {
    const result = await action(transaction)
    await transaction.commit()
    return result
  } catch (error) {
    await transaction.rollback().catch(() => {})
    throw error
  }
}

function seatCoordinates(seat) {
  const match = /^(\d+)排(\d+)座$/.exec(seat)
  return match && { row: Number(match[1]), col: Number(match[2]) }
}

function seatEnabled(hall, seat) {
  const position = seatCoordinates(seat)
  if (!position || position.row < 1 || position.col < 1 || position.row > hall.totalRows || position.col > hall.totalCols) return false
  if (!hall.seatLayout) return true
  try {
    const parsed = JSON.parse(hall.seatLayout)
    const cells = Array.isArray(parsed) ? parsed : parsed.seats
    return cells[position.row - 1][position.col - 1] === 1
  } catch (_) { return false }
}

async function activeHolds(db, showtimeId) {
  const result = await db.collection('mts_seat_hold').where({ showtimeId }).limit(1000).get()
  return (result.data || []).filter(hold => hold.status === 'paid' || (hold.status === 'pending' && hold.expiresAt > Date.now()))
}

async function orderView(db, order) {
  const showtime = await one(db, 'mts_showtime', { id: order.showtimeId }, '场次不存在')
  const [movie, cinema, hall, user] = await Promise.all([
    one(db, 'mts_movie', { id: showtime.movieId }, '电影不存在'),
    one(db, 'mts_cinema', { id: showtime.cinemaId }, '影院不存在'),
    one(db, 'mts_hall', { id: showtime.hallId }, '影厅不存在'),
    one(db, 'mts_user', { id: order.userId }, '用户不存在')
  ])
  return {
    ...clean(order), movieId: movie.id, movieTitle: movie.title, moviePoster: movie.poster,
    cinemaName: cinema.name, hallName: hall.name, showtime: showtime.startTime,
    userName: user.username, statusText: STATUS[order.status] || '未知'
  }
}

async function orderPage(request, db, scope) {
  const { current, size } = pageArgs(request.query)
  let collection = db.collection('mts_order')
  const filter = {}
  if (scope === 'my' || scope === 'history') filter.userId = requireUser(request).id
  if (scope === 'history') filter.status = 1
  if (scope === 'page') {
    requireUser(request, ['ADMIN', 'CINEMA_ADMIN'])
    Object.assign(filter, await orderFilter(request, db))
  }
  if (scope !== 'history') await expiry.cancelExpiredOrders(db, filter)
  if (scope !== 'history' && request.query.status !== undefined && request.query.status !== '') filter.status = Number(request.query.status)
  if (scope === 'page' && request.query.orderNo) filter.orderNo = new RegExp(String(request.query.orderNo).replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i')
  if (Object.keys(filter).length) collection = collection.where(filter)
  const count = await collection.count()
  const result = await collection.orderBy('createTime', 'desc').skip((current - 1) * size).limit(size).get()
  const records = await Promise.all((result.data || []).map(order => orderView(db, order)))
  return success(pageResult(records, count.total, current, size))
}

async function create(request, db, assistantDraftId) {
  const user = requireUser(request)
  const body = request.body
  assert(body && Array.isArray(body.seats) && body.seats.length > 0 && body.seats.length <= 5, 400, '请选择1至5个座位')
  const seats = body.seats.map(seat => String(seat).trim())
  assert(seats.every(Boolean) && new Set(seats).size === seats.length, 400, '座位信息无效或重复')
  const showtimeId = numericId(body.showtimeId)
  const showtime = await one(db, 'mts_showtime', { id: showtimeId }, '场次不存在')
  const hall = await one(db, 'mts_hall', { id: showtime.hallId }, '影厅不存在')
  assert(showtime.status === 0, 409, '该场次已停售')
  assert(millis(showtime.startTime) >= Date.now() + 15 * 60 * 1000, 409, '距离开场不足15分钟，无法购票')
  assert(seats.every(seat => seatEnabled(hall, seat)), 400, '包含无效座位')

  const orderNo = `ORDER${Date.now()}${crypto.randomInt(0, 1000000).toString().padStart(6, '0')}`
  const created = now()
  const order = {
    id: await nextId(db, 'order'), orderNo, userId: user.id, showtimeId, seats: seats.join(','),
    seatCount: seats.length, ticketCount: seats.length, totalPrice: Math.round(Number(showtime.basePrice) * seats.length * 100) / 100,
    originalPrice: Math.round(Number(showtime.basePrice) * seats.length * 100) / 100,
    discountAmount: 0, pointsUsed: 0, pointsEarned: 0, status: 0, isPicked: 0,
    remark: body.remark || '', ...(assistantDraftId ? { assistantDraftId } : {}), createTime: created, updateTime: created
  }
  await withTransaction(db, async transaction => {
    const freshShowtime = (await transaction.collection('mts_showtime').doc(showtime._id).get()).data
    assert(freshShowtime?.status === 0 && freshShowtime.startTime === showtime.startTime, 409, '场次已变化，请刷新后重试')
    await transaction.collection('mts_showtime').doc(showtime._id).update({ bookingVersion: db.command.inc(1) })
    for (const seat of seats) {
      const key = `${showtimeId}:${seat}`
      const reference = transaction.collection('mts_seat_hold').doc(key)
      const found = (await reference.get()).data
      assert(!found || (found.status !== 'paid' && (found.status !== 'pending' || found.expiresAt <= Date.now())), 409, `座位 ${seat} 已被占用`)
      const hold = { showtimeId, seat, orderNo, status: 'pending', expiresAt: Date.now() + HOLD_MS }
      if (found) await reference.update(hold)
      else await transaction.collection('mts_seat_hold').add({ _id: key, ...hold })
    }
    await transaction.collection('mts_order').add(order)
  })
  return success(orderNo, '订单创建成功')
}

async function pay(request, db, order) {
  const userId = requireUser(request).id
  assert(order.userId === userId, 403, '无权操作该订单')
  assert(order.status === 0, 409, '订单状态已变化，请刷新后重试')
  assert(millis(order.createTime) + HOLD_MS > Date.now(), 409, '订单已超时，请重新选座')
  const body = request.body || {}
  const originalCents = Math.round(Number(order.originalPrice) * 100)
  const requestedPoints = Math.floor(Number(body.points || 0) * 10) / 10
  assert(Number.isFinite(requestedPoints) && requestedPoints >= 0, 400, '积分数量无效')
  const coupon = body.couponId ? await one(db, 'mts_user_coupon', { id: numericId(body.couponId) }, '优惠券不存在') : null
  if (coupon) {
    assert(coupon.userId === userId && coupon.status === 0 && millis(coupon.validFrom) <= Date.now() && millis(coupon.expireTime) > Date.now() && Number(coupon.minSpend) <= originalCents / 100, 400, '优惠券不可用')
  }
  const couponCents = Math.min(originalCents, Math.round(Number(coupon?.amount || 0) * 100))
  const user = await one(db, 'mts_user', { id: userId }, '用户不存在')
  assert(requestedPoints <= Number(user.points || 0), 400, '积分余额不足')
  const maxPoints = Math.min(100, originalCents - couponCents)
  assert(requestedPoints <= maxPoints, 400, `本单最多可使用 ${maxPoints} 积分`)
  const pointsCents = Math.round(requestedPoints)
  const paidCents = Math.max(0, originalCents - couponCents - pointsCents)
  const pointsEarned = Math.round((paidCents / 100) * 0.5 * 10) / 10
  const ticketCode = crypto.randomInt(0, 100000000).toString().padStart(8, '0')
  await withTransaction(db, async transaction => {
    const orderResult = await transaction.collection('mts_order').doc(order._id).get()
    assert(orderResult.data?.status === 0, 409, '订单状态已变化')
    const userResult = await transaction.collection('mts_user').doc(user._id).get()
    assert(Number(userResult.data?.points || 0) >= requestedPoints, 409, '积分余额不足')
    for (const seat of order.seats.split(',')) {
      const hold = (await transaction.collection('mts_seat_hold').doc(`${order.showtimeId}:${seat}`).get()).data
      assert(hold && hold.orderNo === order.orderNo && hold.status === 'pending' && hold.expiresAt > Date.now(), 409, `座位 ${seat} 已失效`)
      await transaction.collection('mts_seat_hold').doc(hold._id).update({ status: 'paid' })
    }
    if (coupon) {
      const couponResult = await transaction.collection('mts_user_coupon').doc(coupon._id).get()
      assert(couponResult.data?.status === 0, 409, '优惠券状态已变化')
      await transaction.collection('mts_user_coupon').doc(coupon._id).update({ status: 1, orderId: order.id, useTime: now() })
    }
    await transaction.collection('mts_user').doc(user._id).update({ points: Math.round((Number(userResult.data.points || 0) - requestedPoints + pointsEarned) * 10) / 10, updateTime: now() })
    await transaction.collection('mts_order').doc(order._id).update({ status: 1, totalPrice: paidCents / 100, discountAmount: (couponCents + pointsCents) / 100, pointsUsed: requestedPoints, pointsEarned, couponId: coupon?.id || null, ticketCode, payTime: now(), updateTime: now() })
  })
  return success(null, '支付成功')
}

async function release(request, db, order, refund) {
  await requireOrder(request, db, order)
  assert(order.status === (refund ? 1 : 0), 409, refund ? '只能退票已支付订单' : '只能取消待支付订单')
  if (refund) {
    assert(order.isPicked !== 1, 409, '已取票订单不能退款')
    const showtime = await one(db, 'mts_showtime', { id: order.showtimeId }, '场次不存在')
    assert(millis(showtime.startTime) >= Date.now() + 30 * 60 * 1000, 409, '距离开场不足30分钟，无法退票')
  }
  await withTransaction(db, async transaction => {
    const current = (await transaction.collection('mts_order').doc(order._id).get()).data
    assert(current?.status === (refund ? 1 : 0), 409, '订单状态已变化')
    if (refund) assert(current.isPicked !== 1, 409, '已取票订单不能退款')
    const status = refund ? 3 : 2
    const update = { status, updateTime: now(), ...(refund ? { refundTime: now() } : { cancelTime: now() }) }
    if (refund) {
      const showtime = await one(db, 'mts_showtime', { id: order.showtimeId }, '场次不存在')
      const fee = millis(showtime.startTime) - Date.now() < 24 * 3600 * 1000 ? Math.round(Number(order.totalPrice) * 10) / 100 : 0
      update.remark = fee ? `退票手续费：¥${fee.toFixed(2)}，退款金额：¥${(Number(order.totalPrice) - fee).toFixed(2)}` : `全额退款：¥${Number(order.totalPrice).toFixed(2)}`
    }
    await transaction.collection('mts_order').doc(order._id).update(update)
    for (const seat of order.seats.split(',')) {
      const reference = transaction.collection('mts_seat_hold').doc(`${order.showtimeId}:${seat}`)
      const hold = (await reference.get()).data
      if (hold?.orderNo === order.orderNo) await reference.update({ status: 'released' })
    }
    if (refund) {
      if (order.couponId) {
        const coupon = await one(db, 'mts_user_coupon', { id: order.couponId }, '优惠券不存在')
        await transaction.collection('mts_user_coupon').doc(coupon._id).update({ status: 0, orderId: null, useTime: null })
      }
      const account = await one(db, 'mts_user', { id: order.userId }, '用户不存在')
      const currentUser = (await transaction.collection('mts_user').doc(account._id).get()).data
      await transaction.collection('mts_user').doc(account._id).update({ points: Math.round((Number(currentUser.points || 0) + Number(order.pointsUsed || 0) - Number(order.pointsEarned || 0)) * 10) / 10, updateTime: now() })
    }
  })
  return success(null, refund ? '退票申请已受理' : '订单已取消')
}

async function handle(request, db) {
  const { method, path, query } = request
  const seatsMatch = /^\/showtime\/(\d+)\/seats$/.exec(path)
  if (method === 'GET' && seatsMatch) {
    const showtimeId = numericId(seatsMatch[1])
    const showtime = await one(db, 'mts_showtime', { id: showtimeId }, '场次不存在')
    const hall = await one(db, 'mts_hall', { id: showtime.hallId }, '影厅不存在')
    const occupiedSeats = (await activeHolds(db, showtimeId)).map(hold => hold.seat)
    return success({ totalRows: hall.totalRows, totalCols: hall.totalCols, seatLayout: hall.seatLayout, occupiedSeats, totalSeats: showtime.totalSeats, availableSeats: Math.max(0, showtime.totalSeats - occupiedSeats.length) })
  }
  if (method === 'POST' && path === '/order') return create(request, db)
  if (method === 'POST' && path === '/order/pick') {
    requireUser(request)
    assert(typeof query.ticketCode === 'string' && /^\d{8}$/.test(query.ticketCode), 400, '取票码无效')
    const order = await one(db, 'mts_order', { ticketCode: query.ticketCode }, '取票码无效')
    await requireOrder(request, db, order)
    assert(order.status === 1 && order.isPicked !== 1, 409, '订单未支付或已取票')
    await withTransaction(db, async transaction => {
      const current = (await transaction.collection('mts_order').doc(order._id).get()).data
      assert(current?.status === 1 && current.isPicked !== 1, 409, '订单状态已变化')
      await transaction.collection('mts_order').doc(order._id).update({ isPicked: 1, pickTime: now(), updateTime: now() })
    })
    return success(await orderView(db, { ...order, isPicked: 1, pickTime: now() }), '取票成功')
  }
  if (method === 'GET' && ['/order/my', '/order/page', '/order/history'].includes(path)) return orderPage(request, db, path.split('/')[2])
  const match = /^\/order\/([^/]+)(?:\/(pay|cancel|refund))?$/.exec(path)
  if (match) {
    let order = await one(db, 'mts_order', { orderNo: match[1] }, '订单不存在')
    await requireOrder(request, db, order)
    if (expiry.isExpired(order)) {
      await expiry.cancelExpiredOrder(db, order)
      order = await one(db, 'mts_order', { orderNo: match[1] }, '订单不存在')
      if (match[2] === 'pay' && order.status === 2) throw new ApiError(409, '订单已超时，请重新选座')
    }
    if (method === 'GET' && !match[2]) {
      return success(await orderView(db, order))
    }
    if (method === 'POST' && match[2] === 'pay') return pay(request, db, order)
    if (method === 'POST' && match[2] === 'cancel') return release(request, db, order, false)
    if (method === 'POST' && match[2] === 'refund') return release(request, db, order, true)
  }
  throw new ApiError(404, '接口尚未迁移')
}

module.exports = { handle, create, seatEnabled, millis }
