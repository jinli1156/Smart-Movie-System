'use strict'

const fs = require('fs')
const path = require('path')
const crypto = require('crypto')

const directory = process.argv[2]
if (!directory) throw new Error('用法：node validate-export.js <导出目录>')
const root = path.resolve(directory)
const manifest = JSON.parse(fs.readFileSync(path.join(root, 'manifest.json'), 'utf8'))
const data = {}
for (const [name, count] of Object.entries(manifest.collections)) {
  const content = fs.readFileSync(path.join(root, `${name}.json`), 'utf8')
  const hash = crypto.createHash('sha256').update(content).digest('hex')
  if (hash !== manifest[`${name}Sha256`]) throw new Error(`${name} 文件校验失败`)
  const rows = content.trim() ? content.trimEnd().split('\n').map(line => JSON.parse(line)) : []
  if (rows.length !== count) throw new Error(`${name} 条数不一致`)
  const ids = new Set(rows.map(item => item._id))
  if (ids.size !== rows.length) throw new Error(`${name} 存在重复 _id`)
  data[name] = rows
}

function keyset(name, field = 'id') { return new Set(data[name].map(item => item[field])) }
const users = keyset('mts_user')
const movies = keyset('mts_movie')
const cinemas = keyset('mts_cinema')
const halls = keyset('mts_hall')
const showtimes = keyset('mts_showtime')
const orders = keyset('mts_order')
const orderNumbers = keyset('mts_order', 'orderNo')
const coupons = keyset('mts_user_coupon')
const codes = keyset('mts_coupon_code')
const conversations = keyset('mts_assistant_conversation')
const problems = []
function check(rows, field, valid, source) { for (const row of rows) if (row[field] !== undefined && row[field] !== null && !valid.has(row[field])) problems.push(`${source}.${field}`) }
check(data.mts_hall, 'cinemaId', cinemas, 'hall')
check(data.mts_showtime, 'movieId', movies, 'showtime')
check(data.mts_showtime, 'cinemaId', cinemas, 'showtime')
check(data.mts_showtime, 'hallId', halls, 'showtime')
check(data.mts_order, 'userId', users, 'order')
check(data.mts_order, 'showtimeId', showtimes, 'order')
check(data.mts_user_coupon, 'userId', users, 'user_coupon')
check(data.mts_comment, 'userId', users, 'comment')
check(data.mts_comment, 'movieId', movies, 'comment')
check(data.mts_behavior, 'userId', users, 'behavior')
check(data.mts_behavior, 'movieId', movies, 'behavior')
check(data.mts_coupon_redemption, 'couponCodeId', codes, 'redemption')
check(data.mts_coupon_redemption, 'userCouponId', coupons, 'redemption')
check(data.mts_assistant_conversation, 'userId', users, 'assistant_conversation')
check(data.mts_assistant_turn, 'conversationId', conversations, 'assistant_turn')
check(data.mts_assistant_checkout, 'userId', users, 'assistant_checkout')
check(data.mts_assistant_checkout, 'showtimeId', showtimes, 'assistant_checkout')
check(data.mts_seat_hold, 'showtimeId', showtimes, 'seat_hold')
check(data.mts_seat_hold, 'orderNo', orderNumbers, 'seat_hold')
const ticketCodes = data.mts_order.map(item => item.ticketCode).filter(Boolean)
if (new Set(ticketCodes).size !== ticketCodes.length) problems.push('order.ticketCode 重复')
const activeSeats = new Set(data.mts_seat_hold.map(item => item._id))
if (activeSeats.size !== data.mts_seat_hold.length) problems.push('seat_hold 重复')
for (const user of data.mts_user) if (user.favoriteGenres !== undefined && !Array.isArray(user.favoriteGenres)) problems.push('user.favoriteGenres 类型不匹配')
for (const item of data.mts_assistant_turn) if (!Array.isArray(item.toolResults) || !Array.isArray(item.suggestions)) problems.push('assistant_turn 数组类型不匹配')
for (const item of data.mts_showtime) if (!Number.isFinite(Date.parse(item.startTime)) || !Number.isFinite(Date.parse(item.endTime))) problems.push('showtime 时间无效')
if (problems.length) throw new Error(`导出关系校验失败：${[...new Set(problems)].join('、')}`)
const assetDir = path.join(__dirname, '..', 'frontend', 'movie-ticket-user', 'public', 'uploads')
const assetRefs = new Set()
for (const rows of Object.values(data)) for (const row of rows) {
  for (const match of JSON.stringify(row).matchAll(/\/uploads\/([^"?\\]+)/g)) assetRefs.add(match[1])
}
const missing = [...assetRefs].filter(name => !fs.existsSync(path.join(assetDir, name)))
if (missing.length) throw new Error(`有 ${missing.length} 个旧图片引用缺少文件`)
console.log(`校验通过：${Object.keys(data).length} 个集合，${Object.values(data).reduce((sum, rows) => sum + rows.length, 0)} 条文档`)
console.log(`旧图片引用：${assetRefs.size} 个，缺失：${missing.length} 个`)
