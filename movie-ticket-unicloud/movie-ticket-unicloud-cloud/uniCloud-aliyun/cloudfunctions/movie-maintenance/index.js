'use strict'

const HOLD_MS = 10 * 60 * 1000
function local(milliseconds) { return new Date(milliseconds + 8 * 3600000).toISOString().replace('Z', '+08:00') }

async function cancelExpired(db, clock = Date.now()) {
  const cutoff = local(clock - HOLD_MS)
  const candidates = (await db.collection('mts_order').where({ status: 0, createTime: db.command.lt(cutoff) }).limit(100).get()).data || []
  let cancelled = 0
  for (const order of candidates) {
    const transaction = await db.startTransaction()
    try {
      const current = (await transaction.collection('mts_order').doc(order._id).get()).data
      if (!current || current.status !== 0 || Date.parse(current.createTime) + HOLD_MS > clock) {
        await transaction.rollback()
        continue
      }
      const stamp = local(clock)
      await transaction.collection('mts_order').doc(order._id).update({ status: 2, cancelTime: stamp, updateTime: stamp, remark: '订单超时自动取消' })
      for (const seat of String(current.seats || '').split(',').filter(Boolean)) {
        const reference = transaction.collection('mts_seat_hold').doc(`${current.showtimeId}:${seat}`)
        const hold = (await reference.get()).data
        if (hold?.orderNo === current.orderNo && hold.status === 'pending') await reference.update({ status: 'released' })
      }
      await transaction.commit()
      cancelled++
    } catch (error) {
      await transaction.rollback().catch(() => {})
      console.error('cancel expired order failed', order.orderNo, error)
    }
  }
  return { scanned: candidates.length, cancelled, backlog: candidates.length === 100 }
}

exports.main = async () => cancelExpired(uniCloud.database())
exports.cancelExpired = cancelExpired
