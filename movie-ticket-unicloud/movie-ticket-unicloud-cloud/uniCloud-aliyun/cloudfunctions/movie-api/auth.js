'use strict'

const crypto = require('crypto')
const bcrypt = require('bcryptjs')
const { PNG } = require('pngjs')
const readEnv = require('./read-env')
const { ApiError, assert, success, numericId, pageArgs, pageResult, at, now, clean, nextId, signToken, requireUser } = require('./core')

const FONT = {
  2: ['11111','00001','00001','11111','10000','10000','11111'],
  3: ['11111','00001','00001','11111','00001','00001','11111'],
  4: ['10001','10001','10001','11111','00001','00001','00001'],
  5: ['11111','10000','10000','11111','00001','00001','11111'],
  6: ['11111','10000','10000','11111','10001','10001','11111'],
  7: ['11111','00001','00010','00100','00100','00100','00100'],
  8: ['11111','10001','10001','11111','10001','10001','11111'],
  9: ['11111','10001','10001','11111','00001','00001','11111']
}

function captchaPng(answer) {
  const image = new PNG({ width: 144, height: 52 })
  for (let y = 0; y < 52; y++) for (let x = 0; x < 144; x++) {
    const p = (y * 144 + x) * 4
    const shade = crypto.randomInt(233, 251)
    image.data[p] = shade; image.data[p + 1] = shade - 2; image.data[p + 2] = shade - 11; image.data[p + 3] = 255
  }
  const paint = (x, y, r, g, b) => {
    if (x < 0 || x >= 144 || y < 0 || y >= 52) return
    const p = (y * 144 + x) * 4
    image.data[p] = r; image.data[p + 1] = g; image.data[p + 2] = b
  }
  for (let i = 0; i < 4; i++) {
    const glyph = FONT[answer[i]]
    const left = 14 + i * 32 + crypto.randomInt(-2, 3)
    const top = 9 + crypto.randomInt(-3, 4)
    for (let row = 0; row < glyph.length; row++) for (let col = 0; col < 5; col++) {
      if (glyph[row][col] !== '1') continue
      for (let dy = 0; dy < 4; dy++) for (let dx = 0; dx < 4; dx++) paint(left + col * 4 + dx, top + row * 4 + dy, 64, 69, 55)
    }
  }
  for (let i = 0; i < 180; i++) paint(crypto.randomInt(144), crypto.randomInt(52), 155, 151, 131)
  return 'data:image/png;base64,' + PNG.sync.write(image).toString('base64')
}

function captchaDigest(answer) {
  const secret = readEnv('JWT_SECRET')
  assert(secret && secret.length >= 32, 500, '服务端尚未配置 JWT_SECRET')
  return crypto.createHmac('sha256', secret).update(answer).digest('hex')
}

async function makeCaptcha(db) {
  const answer = Array.from({ length: 4 }, () => '23456789'[crypto.randomInt(8)]).join('')
  const captchaId = crypto.randomUUID()
  await db.collection('mts_captcha').add({ _id: captchaId, digest: captchaDigest(answer), used: false, expiresAt: Date.now() + 120000 })
  return { captchaId, image: captchaPng(answer), expiresIn: 120 }
}

async function consumeCaptcha(db, id, answer) {
  assert(typeof id === 'string' && /^[0-9a-f-]{36}$/i.test(id) && typeof answer === 'string' && answer.trim(), 400, '请输入验证码')
  const result = await db.collection('mts_captcha').where({ _id: id, used: false, expiresAt: db.command.gt(Date.now()) }).updateAndReturn({ used: true })
  assert(result.doc, 400, '验证码已过期，请刷新后重试')
  const expected = Buffer.from(result.doc.digest, 'hex')
  const actual = Buffer.from(captchaDigest(answer.trim()), 'hex')
  assert(crypto.timingSafeEqual(actual, expected), 400, '验证码错误，请重新输入')
}

function userInfo(user) { return clean(user) }

async function oneUser(db, id) {
  const result = await db.collection('mts_user').where({ id }).limit(1).get()
  assert(result.data?.length, 404, '用户不存在')
  return result.data[0]
}

async function handle(request, db) {
  const { method, path, body, query } = request
  if (method === 'GET' && path === '/auth/captcha') return success(await makeCaptcha(db))
  if (method === 'POST' && path === '/auth/login') {
    assert(body && typeof body.username === 'string' && typeof body.password === 'string', 400, '请输入用户名和密码')
    await consumeCaptcha(db, body.captchaId, body.captcha)
    const result = await db.collection('mts_user').where({ username: body.username, deleted: 0 }).limit(1).get()
    const user = result.data?.[0]
    assert(user && bcrypt.compareSync(body.password, user.password), 401, '用户名或密码错误')
    assert(user.status === 1, 403, '账号已被禁用')
    return success({ token: signToken(user), userInfo: userInfo(user) })
  }
  if (method === 'POST' && path === '/auth/register') {
    assert(body && /^[A-Za-z0-9_]{3,50}$/.test(body.username || ''), 400, '用户名格式不正确')
    assert(typeof body.password === 'string' && body.password.length >= 6 && body.password.length <= 72, 400, '密码长度不正确')
    assert(typeof body.nickname === 'string' && body.nickname.trim(), 400, '请输入昵称')
    const found = await db.collection('mts_user').where({ username: body.username }).count()
    assert(found.total === 0, 409, '用户名已存在')
    if (body.phone) {
      const phone = await db.collection('mts_user').where({ phone: body.phone }).count()
      assert(phone.total === 0, 409, '手机号已被注册')
    }
    const user = { id: await nextId(db, 'user'), username: body.username, password: bcrypt.hashSync(body.password, 12), nickname: body.nickname.trim(), ...(body.phone ? { phone: body.phone } : {}), role: 'USER', status: 1, deleted: 0, points: 0, createTime: now(), updateTime: now() }
    try { await db.collection('mts_user').add(user) } catch (error) {
      if (/duplicate|unique/i.test(String(error.message))) throw new ApiError(409, '用户名或手机号已注册')
      throw error
    }
    const couponStart = now()
    await db.collection('mts_user_coupon').add({ id: await nextId(db, 'user_coupon'), userId: user.id, name: '新人专享满 80 减 6.99 元券', amount: 6.99, minSpend: 80, status: 0, validFrom: couponStart, expireTime: at(Date.now() + 7 * 86400000), createTime: couponStart, updateTime: couponStart })
    return success(null, '注册成功')
  }
  if (method === 'GET' && path === '/user/me') return success(userInfo(await oneUser(db, requireUser(request).id)))
  if (method === 'GET' && path === '/user/points') return success((await oneUser(db, requireUser(request).id)).points || 0)
  if (method === 'PUT' && path === '/user/profile') {
    const existing = await oneUser(db, requireUser(request).id)
    assert(body && typeof body === 'object', 400, '请求参数无效')
    const fields = ['nickname', 'gender', 'birthday', 'city', 'address', 'favoriteGenres', 'signature']
    const data = Object.fromEntries(fields.filter(key => Object.prototype.hasOwnProperty.call(body, key)).map(key => [key, body[key]]))
    assert(Object.keys(data).length, 400, '没有可更新的字段')
    await db.collection('mts_user').doc(existing._id).update({ ...data, updateTime: now() })
    return success(userInfo({ ...existing, ...data }))
  }
  if (method === 'POST' && path === '/user/change-password') {
    const existing = await oneUser(db, requireUser(request).id)
    assert(body && bcrypt.compareSync(body.oldPassword || '', existing.password), 400, '旧密码错误')
    assert(typeof body.newPassword === 'string' && body.newPassword.length >= 6 && body.newPassword.length <= 72, 400, '新密码长度不正确')
    await db.collection('mts_user').doc(existing._id).update({ password: bcrypt.hashSync(body.newPassword, 12), updateTime: now() })
    return success(null, '密码修改成功')
  }
  if (method === 'GET' && path === '/user/page') {
    requireUser(request, ['ADMIN'])
    const { current, size } = pageArgs(query)
    const filter = { deleted: 0 }
    if (query.role) filter.role = query.role
    if (query.status !== undefined && query.status !== '') filter.status = Number(query.status)
    if (query.keyword) filter.username = new RegExp(String(query.keyword).replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i')
    const collection = db.collection('mts_user').where(filter)
    const count = await collection.count()
    const result = await collection.orderBy('id', 'asc').skip((current - 1) * size).limit(size).get()
    return success(pageResult((result.data || []).map(userInfo), count.total, current, size))
  }
  const match = path.match(/^\/user\/(\d+)(?:\/(status|reset-password))?$/)
  if (match) {
    requireUser(request, ['ADMIN'])
    const existing = await oneUser(db, numericId(match[1]))
    if (method === 'GET' && !match[2]) return success(userInfo(existing))
    if (method === 'PUT') {
      if (match[2] === 'status') {
        const status = Number(query.status)
        assert(status === 0 || status === 1, 400, '状态无效')
        await db.collection('mts_user').doc(existing._id).update({ status, updateTime: now() })
      } else if (match[2] === 'reset-password') {
        assert(typeof query.password === 'string' && query.password.length >= 6, 400, '新密码长度不正确')
        await db.collection('mts_user').doc(existing._id).update({ password: bcrypt.hashSync(query.password, 12), updateTime: now() })
      } else {
        assert(body && typeof body === 'object', 400, '请求参数无效')
        const fields = ['nickname', 'phone', 'email', 'role', 'status']
        const data = Object.fromEntries(fields.filter(key => Object.prototype.hasOwnProperty.call(body, key)).map(key => [key, body[key]]))
        const role = data.role ?? existing.role
        assert(['USER', 'CINEMA_ADMIN', 'ADMIN'].includes(role), 400, '角色无效')
        assert(data.status === undefined || data.status === 0 || data.status === 1, 400, '账号状态无效')
        data.role = role
        data.cinemaId = null
        if (role === 'CINEMA_ADMIN') {
          const cinemaId = numericId(body.cinemaId)
          const cinema = await db.collection('mts_cinema').where({ id: cinemaId, deleted: 0 }).limit(1).get()
          assert(cinema.data?.length, 400, '请分配有效的所属影院')
          data.cinemaId = cinemaId
        }
        await db.collection('mts_user').doc(existing._id).update({ ...data, updateTime: now() })
      }
      return success(null, '更新成功')
    }
    if (method === 'DELETE' && !match[2]) {
      assert(existing.id !== request.user.id, 400, '不能删除当前登录账号')
      await db.collection('mts_user').doc(existing._id).remove()
      return success(null, '删除成功')
    }
  }
  throw new ApiError(404, '接口尚未迁移')
}

module.exports = { handle }
