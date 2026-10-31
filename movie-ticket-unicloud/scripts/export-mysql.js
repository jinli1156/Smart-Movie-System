'use strict'

// Read-only, consistent MySQL snapshot. Output remains under the ignored .local directory.
const fs = require('fs')
const path = require('path')
const crypto = require('crypto')
const mysql = require('mysql2/promise')

const TABLES = [
  ['user', 'mts_user'], ['cinema', 'mts_cinema'], ['hall', 'mts_hall'],
  ['movie', 'mts_movie'], ['showtime', 'mts_showtime'], ['ticket_order', 'mts_order'],
  ['user_coupon', 'mts_user_coupon'], ['comment', 'mts_comment'],
  ['user_movie_behavior', 'mts_behavior'], ['coupon_code', 'mts_coupon_code'],
  ['coupon_code_redemption', 'mts_coupon_redemption'],
  ['assistant_conversation', 'mts_assistant_conversation'],
  ['assistant_turn', 'mts_assistant_turn'],
  ['assistant_preference', 'mts_assistant_preference'],
  ['assistant_checkout', 'mts_assistant_checkout'],
  ['scheduling_saved_draft', 'mts_scheduling_draft']
]
const JSON_FIELDS = new Set(['favoriteGenres', 'preferences', 'criteria', 'snapshot', 'watchPlan', 'toolResults', 'suggestions', 'content'])
const NUMERIC_FIELDS = new Set(['id', 'userId', 'movieId', 'cinemaId', 'hallId', 'showtimeId', 'orderId', 'couponId', 'couponCodeId', 'userCouponId', 'amount', 'minSpend', 'points', 'pointsUsed', 'pointsEarned', 'totalPrice', 'originalPrice', 'discountAmount', 'basePrice', 'rating', 'lng', 'lat'])

function camel(value) { return value.replace(/_([a-z])/g, (_, letter) => letter.toUpperCase()) }
function normalize(value, field) {
  if (value === null) return undefined
  if (Buffer.isBuffer(value)) value = value.toString('utf8')
  if (typeof value === 'string') value = value.replace(/https?:\/\/(?:localhost|127\.0\.0\.1)(?::\d+)?\/api\/uploads\//g, '/uploads/').replace(/\/api\/uploads\//g, '/uploads/')
  if (JSON_FIELDS.has(field) && typeof value === 'string') {
    try { return JSON.parse(value) } catch (_) { return value }
  }
  if (NUMERIC_FIELDS.has(field) && typeof value === 'string' && /^-?\d+(?:\.\d+)?$/.test(value)) return Number(value)
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(?:\.\d+)?$/.test(value)) return value.replace(' ', 'T') + '+08:00'
  return value
}

function document(row, collection) {
  const result = {}
  for (const [key, value] of Object.entries(row)) {
    const field = camel(key)
    const normalized = normalize(value, field)
    if (normalized !== undefined) result[field] = normalized
  }
  if (['mts_user', 'mts_movie', 'mts_cinema', 'mts_hall', 'mts_showtime', 'mts_comment'].includes(collection)) result.deleted = Number(result.deleted || 0)
  if (collection === 'mts_order') {
    result.seatCount = Number(result.seatCount || result.ticketCount || String(result.seats || '').split(',').filter(Boolean).length)
    result.ticketCount = result.seatCount
  }
  if (collection === 'mts_assistant_conversation') {
    result.updatedAt = result.updateTime
    result.watchPlan = result.watchPlan || {}
  }
  if (collection === 'mts_assistant_turn') {
    result.createdAt = result.createTime
    result.toolResults = result.toolResults || []
    result.suggestions = result.suggestions || []
  }
  if (collection === 'mts_assistant_preference') result.preferences = result.preferences || {}
  if (collection === 'mts_assistant_checkout') {
    result.status = result.orderNo ? 'completed' : Date.parse(result.expiresAt) > Date.now() ? 'prepared' : 'expired'
    result.snapshot = result.snapshot || {}
  }
  result._id = `${collection}:${result.id === undefined ? result.userId : result.id}`
  return result
}

function holdsFromOrders(orders) {
  const holds = new Map()
  for (const order of orders) {
    if (![0, 1].includes(order.status)) continue
    const pending = order.status === 0
    const expiresAt = Date.parse(order.createTime) + 10 * 60000
    if (pending && expiresAt <= Date.now()) continue
    for (const seat of String(order.seats || '').split(',').map(item => item.trim()).filter(Boolean)) {
      const id = `${order.showtimeId}:${seat}`
      if (holds.has(id)) throw new Error(`座位记录重复：${id}。需要人工核对源库订单后才能导入。`)
      holds.set(id, { _id: id, showtimeId: order.showtimeId, seat, orderNo: order.orderNo, status: pending ? 'pending' : 'paid', expiresAt: pending ? expiresAt : 0 })
    }
  }
  return [...holds.values()]
}

async function main() {
  if (!process.env.DB_PASSWORD) throw new Error('请通过 DB_PASSWORD 环境变量提供本地 MySQL 密码')
  const connection = await mysql.createConnection({ host: process.env.DB_HOST || '127.0.0.1', port: Number(process.env.DB_PORT || 3306), user: process.env.DB_USER || 'root', password: process.env.DB_PASSWORD, database: process.env.DB_NAME || 'movie_ticket', dateStrings: true, supportBigNumbers: true })
  const data = {}
  try {
    await connection.query('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ')
    await connection.query('START TRANSACTION WITH CONSISTENT SNAPSHOT')
    for (const [table, collection] of TABLES) {
      const [rows] = await connection.query(`SELECT * FROM \`${table}\``)
      data[collection] = rows.map(row => document(row, collection))
    }
    await connection.query('COMMIT')
  } catch (error) { await connection.query('ROLLBACK').catch(() => {}); throw error }
  finally { await connection.end() }
  data.mts_seat_hold = holdsFromOrders(data.mts_order)
  data.mts_counter = Object.entries(data).filter(([name, rows]) => name !== 'mts_counter' && rows.some(item => Number.isSafeInteger(item.id))).map(([name, rows]) => ({ _id: name.slice(4).replace(/^assistant_conversation$/, 'assistant_conversation'), seq: Math.max(...rows.map(item => Number(item.id || 0))) }))
  const manifest = { exportedAt: new Date().toISOString(), source: process.env.DB_NAME || 'movie_ticket', collections: Object.fromEntries(Object.entries(data).map(([name, rows]) => [name, rows.length])) }
  console.log(JSON.stringify(manifest, null, 2))
  if (!process.argv.includes('--write')) return
  const directory = path.join(__dirname, '..', '.local', `mysql-export-${new Date().toISOString().replace(/[:.]/g, '-')}`)
  fs.mkdirSync(directory, { recursive: true })
  for (const [name, rows] of Object.entries(data)) {
    const json = rows.map(row => JSON.stringify(row)).join('\n') + (rows.length ? '\n' : '')
    fs.writeFileSync(path.join(directory, `${name}.json`), json, { flag: 'wx' })
    manifest[`${name}Sha256`] = crypto.createHash('sha256').update(json).digest('hex')
  }
  fs.writeFileSync(path.join(directory, 'manifest.json'), JSON.stringify(manifest, null, 2), { flag: 'wx' })
  console.log(`仅保存在本机、未跟踪的导出目录：${directory}`)
}

main().catch(error => { console.error(error.message); process.exitCode = 1 })

module.exports = { document, holdsFromOrders }
