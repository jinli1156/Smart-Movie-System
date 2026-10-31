'use strict'

const { at } = require('./core')

const HOLD_MS = 10 * 60 * 1000

function millis(value) {
  if (typeof value === 'number') return value
  if (value instanceof Date) return value.getTime()
  const text = String(value || '').replace(' ', 'T')
  return Date.parse(/[Zz]|[+-]\d\d:\d\d$/.test(text) ? text : `${text}+08:00`)
}

function isExpired(order, clock = Date.now()) {
  const created = millis(order?.createTime)
  return order?.status === 0 && Number.isFinite(created) && created + HOLD_MS <= clock
}

async function cancelExpiredOrder(db, order, clock = Date.now()) {
  if (!isExpired(order, clock)) return false
  const transaction = await db.startTransaction()
  try {
    const current = (await transaction.collection('mts_order').doc(order._id).get()).data
    if (!isExpired(current, clock)) {
      await transaction.rollback()
      return false
    }
    const stamp = at(clock)
    await transaction.collection('mts_order').doc(order._id).update({ status: 2, cancelTime: stamp, updateTime: stamp, remark: '订单超时自动取消' })
    for (const seat of String(current.seats || '').split(',').filter(Boolean)) {
      const reference = transaction.collection('mts_seat_hold').doc(`${current.showtimeId}:${seat}`)
      const hold = (await reference.get()).data
      if (hold?.orderNo === current.orderNo && hold.status === 'pending') await reference.update({ status: 'released' })
    }
    await transaction.commit()
    return true
  } catch (error) {
    await transaction.rollback().catch(() => {})
    const latest = (await db.collection('mts_order').doc(order._id).get()).data
    if (!isExpired(latest, clock)) return false
    throw error
  }
}

async function cancelExpiredOrders(db, scope = {}, clock = Date.now()) {
  const cutoff = at(clock - HOLD_MS)
  const filter = { ...scope, status: 0, createTime: db.command.lt(cutoff) }
  const candidates = (await db.collection('mts_order').where(filter).limit(20).get()).data || []
  let cancelled = 0
  for (const order of candidates) if (await cancelExpiredOrder(db, order, clock)) cancelled++
  return { scanned: candidates.length, cancelled, backlog: candidates.length === 20 }
}

module.exports = { HOLD_MS, isExpired, cancelExpiredOrder, cancelExpiredOrders }
