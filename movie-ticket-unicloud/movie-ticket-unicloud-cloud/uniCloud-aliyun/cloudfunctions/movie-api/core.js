'use strict'

const crypto = require('crypto')
const readEnv = require('./read-env')

class ApiError extends Error {
  constructor(code, message) {
    super(message)
    this.code = code
  }
}

function assert(condition, code, message) {
  if (!condition) throw new ApiError(code, message)
}

function success(data = null, message = '操作成功') {
  return { code: 200, message, data }
}

function failure(error) {
  if (error instanceof ApiError) return { code: error.code, message: error.message, data: null }
  console.error('movie-api failed', error)
  return { code: 500, message: '服务暂时不可用', data: null }
}

function parseEvent(event) {
  const headers = Object.fromEntries(Object.entries(event.headers || {}).map(([key, value]) => [key.toLowerCase(), value]))
  const method = (event.httpMethod || 'GET').toUpperCase()
  const path = ('/' + (event.path || '/').replace(/^\/+/, '')).replace(/\/+$/, '') || '/'
  let body = event.body || ''
  if ((event.isBase64Encoded === true || event.isBase64Encoded === 'true') && typeof body === 'string') body = Buffer.from(body, 'base64')
  if (Buffer.isBuffer(body) && (headers['content-type'] || '').includes('application/json')) body = body.toString('utf8')
  if (typeof body === 'string' && body && (headers['content-type'] || '').includes('application/json')) {
    try { body = JSON.parse(body) } catch (_) { throw new ApiError(400, '请求体不是有效的 JSON') }
  }
  return { method, path, headers, query: event.queryStringParameters || {}, body }
}

// 跨域响应头由 URL 化网关统一添加，云函数不输出 CORS 头，否则会出现重复头导致浏览器拒绝。
function responseHeaders() {
  return {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store'
  }
}

function response(payload, origin, statusCode = 200) {
  return { mpserverlessComposedResponse: true, statusCode, headers: responseHeaders(), body: JSON.stringify(payload) }
}

function numericId(value) {
  const id = Number(value)
  assert(Number.isSafeInteger(id) && id > 0, 400, '无效的 ID')
  return id
}

function pageArgs(query) {
  const current = Math.max(1, Math.min(100000, Number(query.page) || 1))
  const size = Math.max(1, Math.min(100, Number(query.size) || 10))
  return { current, size }
}

function pageResult(records, total, current, size) {
  return { records, total, current, size, pages: Math.ceil(total / size) }
}

function at(milliseconds) { return new Date(milliseconds + 8 * 3600000).toISOString().replace('Z', '+08:00') }
function now() { return at(Date.now()) }

function clean(doc) {
  if (!doc) return null
  const { _id, password, ...result } = doc
  return result
}

async function nextId(db, name) {
  const counters = db.collection('mts_counter')
  const existing = await counters.doc(name).get()
  if (!existing.data || !existing.data.length) {
    try { await counters.add({ _id: name, seq: 0 }) } catch (_) { /* another request created it */ }
  }
  const result = await counters.where({ _id: name }).updateAndReturn({ seq: db.command.inc(1) })
  assert(result.doc && Number.isSafeInteger(result.doc.seq), 500, '生成编号失败')
  return result.doc.seq
}

function signToken(user) {
  const secret = readEnv('JWT_SECRET')
  assert(secret && secret.length >= 32, 500, '服务端尚未配置 JWT_SECRET')
  const header = Buffer.from(JSON.stringify({ alg: 'HS256', typ: 'JWT' })).toString('base64url')
  const claims = Buffer.from(JSON.stringify({ sub: String(user.id), role: user.role, exp: Math.floor(Date.now() / 1000) + 7 * 86400 })).toString('base64url')
  const signature = crypto.createHmac('sha256', secret).update(`${header}.${claims}`).digest('base64url')
  return `${header}.${claims}.${signature}`
}

function verifyToken(value) {
  const secret = readEnv('JWT_SECRET')
  if (!secret || !value || !value.startsWith('Bearer ')) return null
  const token = value.slice(7)
  const parts = token.split('.')
  if (parts.length !== 3) return null
  const expected = crypto.createHmac('sha256', secret).update(`${parts[0]}.${parts[1]}`).digest()
  let actual
  try { actual = Buffer.from(parts[2], 'base64url') } catch (_) { return null }
  if (actual.length !== expected.length || !crypto.timingSafeEqual(actual, expected)) return null
  try {
    const claims = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'))
    if (claims.exp <= Date.now() / 1000) return null
    return { id: numericId(claims.sub), role: claims.role }
  } catch (_) { return null }
}

function requireUser(request, roles) {
  assert(request.user, 401, '请先登录')
  if (roles) assert(roles.includes(request.user.role), 403, '无权执行此操作')
  return request.user
}

module.exports = { ApiError, assert, success, failure, parseEvent, response, numericId, pageArgs, pageResult, at, now, clean, nextId, signToken, verifyToken, requireUser }
