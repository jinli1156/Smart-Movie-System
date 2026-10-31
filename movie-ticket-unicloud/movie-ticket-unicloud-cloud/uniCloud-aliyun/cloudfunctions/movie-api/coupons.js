'use strict'

const { ApiError, assert, success, numericId, pageArgs, pageResult, at, now, clean, nextId, requireUser } = require('./core')
const { millis } = require('./orders')

function view(coupon) {
  const status = coupon.status === 0 && millis(coupon.expireTime) <= Date.now() ? 2 : coupon.status
  return { ...clean(coupon), status, statusText: { 0: '可使用', 1: '已使用', 2: '已过期' }[status] || '未知', available: status === 0 }
}

async function one(db, collection, filter, message) {
  const found = await db.collection(collection).where(filter).limit(1).get()
  assert(found.data?.length, 404, message)
  return found.data[0]
}

async function transaction(db, action) {
  const tx = await db.startTransaction()
  try { const result = await action(tx); await tx.commit(); return result }
  catch (error) { await tx.rollback().catch(() => {}); throw error }
}

function validateCodeBody(body) {
  assert(body && /^[A-Z0-9_-]{4,32}$/i.test(body.code || ''), 400, '兑换码格式无效')
  assert(typeof body.name === 'string' && body.name.trim(), 400, '请输入优惠券名称')
  for (const field of ['amount', 'minSpend', 'validDays', 'maxRedemptions']) assert(Number.isFinite(Number(body[field])) && Number(body[field]) >= 0, 400, `${field}无效`)
  assert(millis(body.validFrom) < millis(body.expireTime), 400, '有效时间无效')
  return {
    code: body.code.trim().toUpperCase(), name: body.name.trim(), amount: Number(body.amount),
    minSpend: Number(body.minSpend), validDays: Number(body.validDays),
    maxRedemptions: Number(body.maxRedemptions), validFrom: body.validFrom,
    expireTime: body.expireTime, status: Number(body.status)
  }
}

async function handle(request, db) {
  const { method, path, query, body } = request
  if (method === 'GET' && path === '/coupon/my') {
    const userId = requireUser(request).id
    const result = await db.collection('mts_user_coupon').where({ userId }).orderBy('expireTime', 'asc').limit(1000).get()
    return success((result.data || []).map(view).sort((a, b) => a.status - b.status || b.amount - a.amount))
  }
  if (method === 'POST' && path === '/coupon/redeem') {
    const userId = requireUser(request).id
    assert(body && typeof body.code === 'string' && body.code.trim(), 400, '请输入兑换码')
    const code = await one(db, 'mts_coupon_code', { code: body.code.trim().toUpperCase() }, '兑换码不存在')
    assert(code.status === 1, 409, '兑换码已停用')
    assert(millis(code.validFrom) <= Date.now() && millis(code.expireTime) > Date.now(), 400, '兑换码不在有效期内')
    const previous = await db.collection('mts_coupon_redemption').where({ couponCodeId: code.id, userId }).count()
    assert(previous.total === 0, 409, '该兑换码每个账号只能兑换一次')
    const coupon = { id: await nextId(db, 'user_coupon'), userId, name: code.name, amount: code.amount, minSpend: code.minSpend, status: 0, validFrom: now(), expireTime: at(Date.now() + Number(code.validDays) * 86400000), createTime: now(), updateTime: now() }
    const redemption = { id: await nextId(db, 'coupon_redemption'), couponCodeId: code.id, userId, userCouponId: coupon.id, redeemTime: now(), createTime: now() }
    await transaction(db, async tx => {
      const current = (await tx.collection('mts_coupon_code').doc(code._id).get()).data
      assert(current?.status === 1 && current.redeemedCount < current.maxRedemptions, 409, '兑换码已达到领取上限')
      await tx.collection('mts_user_coupon').add(coupon)
      await tx.collection('mts_coupon_redemption').add(redemption)
      await tx.collection('mts_coupon_code').doc(code._id).update({ redeemedCount: db.command.inc(1), updateTime: now() })
    })
    return success(view(coupon), '兑换成功')
  }
  if (!path.startsWith('/admin/coupon-codes')) throw new ApiError(404, '接口尚未迁移')
  requireUser(request, ['ADMIN'])
  if (method === 'GET' && path === '/admin/coupon-codes') {
    const { current, size } = pageArgs(query)
    const filter = {}
    if (query.status !== undefined && query.status !== '') filter.status = Number(query.status)
    if (query.keyword) filter.code = new RegExp(String(query.keyword).replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i')
    let collection = db.collection('mts_coupon_code')
    if (Object.keys(filter).length) collection = collection.where(filter)
    const count = await collection.count()
    const result = await collection.orderBy('id', 'desc').skip((current - 1) * size).limit(size).get()
    return success(pageResult((result.data || []).map(clean), count.total, current, size))
  }
  if (method === 'GET' && path === '/admin/coupon-codes/redemptions') {
    const { current, size } = pageArgs(query)
    let collection = db.collection('mts_coupon_redemption')
    if (query.couponCodeId) collection = collection.where({ couponCodeId: numericId(query.couponCodeId) })
    const count = await collection.count()
    const result = await collection.orderBy('redeemTime', 'desc').skip((current - 1) * size).limit(size).get()
    const records = await Promise.all((result.data || []).map(async redemption => {
      const [user, code, coupon] = await Promise.all([
        one(db, 'mts_user', { id: redemption.userId }, '用户不存在'),
        one(db, 'mts_coupon_code', { id: redemption.couponCodeId }, '兑换码不存在'),
        one(db, 'mts_user_coupon', { id: redemption.userCouponId }, '优惠券不存在')
      ])
      return { ...clean(redemption), code: code.code, couponName: code.name, username: user.username, nickname: user.nickname, couponStatus: coupon.status, useTime: coupon.useTime }
    }))
    return success(pageResult(records, count.total, current, size))
  }
  if (method === 'POST' && path === '/admin/coupon-codes') {
    const data = validateCodeBody(body)
    data.id = await nextId(db, 'coupon_code')
    data.redeemedCount = 0
    data.createTime = data.updateTime = now()
    try { await db.collection('mts_coupon_code').add(data) }
    catch (error) { if (/duplicate|unique/i.test(String(error.message))) throw new ApiError(409, '兑换码已存在'); throw error }
    return success(data, '兑换码创建成功')
  }
  const match = /^\/admin\/coupon-codes\/(\d+)(?:\/(status))?$/.exec(path)
  if (match) {
    const code = await one(db, 'mts_coupon_code', { id: numericId(match[1]) }, '兑换码不存在')
    if (method === 'PUT' && match[2] === 'status') {
      const status = Number(query.status)
      assert(status === 0 || status === 1, 400, '状态无效')
      await db.collection('mts_coupon_code').doc(code._id).update({ status, updateTime: now() })
      return success(null, status ? '兑换码已启用' : '兑换码已停用')
    }
    if (method === 'PUT' && !match[2]) {
      const data = validateCodeBody(body)
      await db.collection('mts_coupon_code').doc(code._id).update({ ...data, updateTime: now() })
      return success({ ...clean(code), ...data }, '兑换码更新成功')
    }
    if (method === 'DELETE' && !match[2]) {
      const redemptions = await db.collection('mts_coupon_redemption').where({ couponCodeId: code.id }).count()
      assert(redemptions.total === 0, 409, '已有兑换记录，不能删除')
      await db.collection('mts_coupon_code').doc(code._id).remove()
      return success(null, '兑换码删除成功')
    }
  }
  throw new ApiError(404, '接口尚未迁移')
}

module.exports = { handle }
