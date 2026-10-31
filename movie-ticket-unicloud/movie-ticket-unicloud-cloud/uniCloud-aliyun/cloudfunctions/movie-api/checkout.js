'use strict'

const crypto = require('crypto')
const { ApiError, assert, success, numericId, at, now, requireUser } = require('./core')
const orders = require('./orders')
const seats = require('./seats')
const pricing = require('./pricing')

async function one(db, collection, id, message) {
  const found = await db.collection(collection).where({ id }).limit(1).get()
  assert(found.data?.length, 404, message)
  return found.data[0]
}

async function preview(db, userId, criteria, requestedSeats) {
  assert(criteria && typeof criteria === 'object' && !Array.isArray(criteria), 400, '观影条件无效')
  const showtimeId = numericId(criteria.showtimeId)
  const people = Number(criteria.people)
  assert(Number.isSafeInteger(people) && people >= 1 && people <= 5, 400, '人数须为1至5人')
  const showtime = await one(db, 'mts_showtime', showtimeId, '场次不存在')
  const [movie, cinema, hall] = await Promise.all([
    one(db, 'mts_movie', showtime.movieId, '影片不存在'),
    one(db, 'mts_cinema', showtime.cinemaId, '影院不存在'),
    one(db, 'mts_hall', showtime.hallId, '影厅不存在')
  ])
  assert(showtime.status === 0 && movie.status === 1 && cinema.status === 1 && hall.status === 1 && orders.millis(showtime.startTime) > Date.now() + 15 * 60000, 409, '该场次暂不可购票')
  const holds = (await db.collection('mts_seat_hold').where({ showtimeId }).limit(1000).get()).data || []
  const occupied = new Set(holds.filter(item => item.status === 'paid' || item.status === 'pending' && item.expiresAt > Date.now()).map(item => item.seat))
  const selected = requestedSeats?.length ? requestedSeats : seats.recommend(hall, showtime, occupied, people, seats.infer(criteria.naturalLanguage, criteria.preference), 1)[0]?.seats
  assert(Array.isArray(selected) && selected.length === people && new Set(selected).size === people, 409, '暂时找不到符合人数的座位')
  assert(selected.every(item => orders.seatEnabled(hall, item) && !occupied.has(item)), 409, '所选座位已不可用')
  if (criteria.requireTogether !== false && people > 1) {
    const positions = selected.map(value => /^(\d+)排(\d+)座$/.exec(value)).sort((a, b) => Number(a?.[2]) - Number(b?.[2]))
    assert(positions.every(Boolean) && positions.every(item => item[1] === positions[0][1]) && positions.every((item, index) => index === 0 || Number(item[2]) === Number(positions[index - 1][2]) + 1), 409, '所选座位不满足连座要求')
  }
  const originalPrice = Math.round(Number(showtime.basePrice) * people * 100) / 100
  const account = await pricing.discounts(db, userId)
  const bestPlan = pricing.plans(originalPrice, account.points, criteria.useCoupons === false ? [] : account.coupons, criteria.usePoints !== false)[0]
  return { showtimeId, movieId: movie.id, movieTitle: movie.title, moviePoster: movie.poster, cinemaName: cinema.name, hallName: hall.name, startTime: showtime.startTime, endTime: showtime.endTime, people, seats: selected, originalPrice, bestPlan, usePoints: criteria.usePoints !== false, useCoupons: criteria.useCoupons !== false }
}

function receipt(order, snapshot) {
  return { orderNo: order.orderNo, status: order.status, usePoints: snapshot.usePoints, useCoupons: snapshot.useCoupons, ...(snapshot.bestPlan.couponId ? { couponId: snapshot.bestPlan.couponId } : {}), message: order.status === 0 ? '订单已创建，请在支付页完成支付' : '请查看当前订单状态' }
}

async function ownDraft(db, id, userId) {
  const found = await db.collection('mts_assistant_checkout').where({ id, userId }).limit(1).get()
  assert(found.data?.length, 404, '未找到当前账号的订单确认卡')
  return found.data[0]
}

async function linkedOrder(db, id, userId) {
  return (await db.collection('mts_order').where({ assistantDraftId: id, userId }).limit(1).get()).data?.[0]
}

async function handle(request, db) {
  const { method, path, body } = request
  const userId = requireUser(request).id
  if (method === 'POST' && path === '/assistant/checkout/prepare') {
    const criteria = body?.criteria
    const snapshot = await preview(db, userId, criteria, body?.seats)
    const id = crypto.randomUUID()
    const expiresAt = at(Date.now() + 5 * 60000)
    await db.collection('mts_assistant_checkout').add({ id, userId, showtimeId: snapshot.showtimeId, criteria, snapshot, status: 'prepared', expiresAt, createTime: now() })
    return success({ ...snapshot, draftId: id, expiresAt, status: 'AWAITING_CONFIRMATION', criteria, notice: '请核对场次和座位。确认时创建待支付订单；当前尚未锁座，支付时重新核验优惠。' })
  }
  const match = /^\/assistant\/checkout\/([0-9a-fA-F-]{36})\/confirm$/.exec(path)
  if (method === 'POST' && match) {
    const id = match[1]
    const draft = await ownDraft(db, id, userId)
    const existing = await linkedOrder(db, id, userId)
    if (existing) return success(receipt(existing, draft.snapshot))
    assert(Date.parse(draft.expiresAt) > Date.now(), 409, '确认卡已过期，请重新准备')
    assert(draft.status === 'prepared' || draft.status === 'processing' && Date.parse(draft.processingAt) + 65000 < Date.now(), 409, '确认卡正在处理，请稍后重试')
    const current = await preview(db, userId, draft.criteria, draft.snapshot.seats)
    assert(['showtimeId', 'movieTitle', 'cinemaName', 'hallName', 'startTime', 'endTime', 'originalPrice'].every(field => current[field] === draft.snapshot[field]) && JSON.stringify(current.bestPlan) === JSON.stringify(draft.snapshot.bestPlan), 409, '场次或优惠报价已变化，请重新准备')
    const claim = await db.collection('mts_assistant_checkout').where({ _id: draft._id, status: draft.status, ...(draft.status === 'processing' ? { processingAt: draft.processingAt } : {}) }).updateAndReturn({ status: 'processing', processingAt: now() })
    assert(claim.doc, 409, '确认卡正在处理')
    let orderNo
    try {
      const created = await orders.create({ user: request.user, body: { showtimeId: draft.showtimeId, seats: draft.snapshot.seats, remark: '影票观 AI 确认下单' } }, db, id)
      orderNo = created.data
    } catch (error) {
      const recovered = await linkedOrder(db, id, userId)
      if (recovered) orderNo = recovered.orderNo
      else {
        await db.collection('mts_assistant_checkout').doc(draft._id).update({ status: 'prepared' })
        throw error
      }
    }
    await db.collection('mts_assistant_checkout').doc(draft._id).update({ status: 'completed', orderNo, updateTime: now() })
    const order = (await db.collection('mts_order').where({ orderNo, userId }).limit(1).get()).data?.[0]
    assert(order, 502, '订单创建结果尚未确认，请查看我的订单')
    return success(receipt(order, draft.snapshot))
  }
  throw new ApiError(404, '接口不存在')
}

module.exports = { handle, preview }
