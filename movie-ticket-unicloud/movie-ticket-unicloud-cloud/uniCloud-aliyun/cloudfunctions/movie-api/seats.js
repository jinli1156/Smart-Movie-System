'use strict'

const crypto = require('crypto')
const { ApiError, assert, success, numericId, now } = require('./core')
const PREFERENCES = new Set(['CENTER', 'QUIET', 'EASY_ACCESS', 'COUPLE', 'ACCESSIBLE'])
const LABELS = { CENTER: '中央视野', QUIET: '靠后安静', EASY_ACCESS: '方便进出', COUPLE: '情侣双人', ACCESSIBLE: '无障碍友好' }

function infer(text, fallback) {
  const value = String(text || '').toLowerCase()
  if (/无障碍|轮椅|行动不便|老人|残障/.test(value)) return 'ACCESSIBLE'
  if (/情侣|约会|两个人|二人|双人/.test(value)) return 'COUPLE'
  if (/方便进出|出口|通道|过道|靠边|厕所/.test(value)) return 'EASY_ACCESS'
  if (/安静|靠后|后排|人少/.test(value)) return 'QUIET'
  if (/中央|中间|正中|视野|最佳位置/.test(value)) return 'CENTER'
  return PREFERENCES.has(fallback) ? fallback : 'CENTER'
}

function layout(hall) {
  if (!hall.seatLayout) return null
  try {
    const value = JSON.parse(hall.seatLayout)
    return Array.isArray(value) ? value : value.seats
  } catch (_) { throw new ApiError(500, '影厅座位图数据无效') }
}

function recommend(hall, showtime, occupied, people, preference, limit) {
  const cells = layout(hall)
  const rows = Number(hall.totalRows)
  const cols = Number(hall.totalCols)
  const candidates = []
  for (let row = 1; row <= rows; row++) for (let first = 1; first + people - 1 <= cols; first++) {
    const seats = Array.from({ length: people }, (_, index) => `${row}排${first + index}座`)
    if (seats.some((seat, index) => occupied.has(seat) || cells && cells[row - 1]?.[first + index - 1] !== 1)) continue
    const center = first + (people - 1) / 2
    const visionCenter = Math.round(100 * (1 - Math.abs(center - (cols + 1) / 2) / Math.max(1, cols / 2)))
    const goldenDistance = Math.round(100 * (1 - Math.abs(row - rows * 0.62) / Math.max(1, rows)))
    const accessConvenience = Math.round(100 * (1 - Math.min(first - 1, cols - (first + people - 1)) / Math.max(1, cols / 2)))
    const quiet = Math.round(row / rows * 100)
    const score = preference === 'EASY_ACCESS' || preference === 'ACCESSIBLE' ? accessConvenience * 0.5 + visionCenter * 0.2 + goldenDistance * 0.3
      : preference === 'QUIET' ? quiet * 0.5 + visionCenter * 0.25 + goldenDistance * 0.25
      : preference === 'COUPLE' ? visionCenter * 0.45 + goldenDistance * 0.4 + 15
      : visionCenter * 0.55 + goldenDistance * 0.45
    const match = Math.max(1, Math.min(99, Math.round(score)))
    const seatLabel = seats.join('、')
    candidates.push({ title: LABELS[preference], icon: 'location_on', match, seatLabel, seats, count: people, unitPrice: showtime.basePrice, reason: `连续${people}座，${LABELS[preference]}`, reasonSource: 'algorithm', tags: ['实时可选', '连座'], scores: { visionCenter, goldenDistance, continuity: 100, accessConvenience, preference: match, realtimeAvailability: 100 }, singleSeatPenalty: 0 })
  }
  return candidates.sort((a, b) => b.match - a.match || a.seatLabel.localeCompare(b.seatLabel)).slice(0, limit)
}

async function handle(request, db) {
  const match = /^\/showtime\/(\d+)\/seat-recommendation$/.exec(request.path)
  if (!match || !['GET', 'POST'].includes(request.method)) throw new ApiError(404, '接口不存在')
  const showtimeId = numericId(match[1])
  const parameters = request.method === 'GET' ? request.query : request.body || {}
  const people = Number(parameters.people || 2)
  const limit = Number(parameters.limit || 3)
  assert(Number.isSafeInteger(people) && people >= 1 && people <= 5, 400, '人数须为1至5人')
  assert(Number.isSafeInteger(limit) && limit >= 1 && limit <= 5, 400, '推荐数量须为1至5组')
  const showtime = (await db.collection('mts_showtime').where({ id: showtimeId }).limit(1).get()).data?.[0]
  assert(showtime, 404, '场次不存在')
  const hall = (await db.collection('mts_hall').where({ id: showtime.hallId }).limit(1).get()).data?.[0]
  assert(hall, 404, '影厅不存在')
  const holds = (await db.collection('mts_seat_hold').where({ showtimeId }).limit(1000).get()).data || []
  const occupied = new Set(holds.filter(item => item.status === 'paid' || item.status === 'pending' && item.expiresAt > Date.now()).map(item => item.seat))
  const preference = infer(parameters.naturalLanguage, parameters.preference)
  const items = recommend(hall, showtime, occupied, people, preference, limit)
  if (request.method === 'GET') return success(items)
  const availabilityToken = crypto.createHash('sha256').update([...occupied].sort().join('|')).digest('hex').slice(0, 12)
  return success({ recommendations: items, interpretedPreference: preference, preferenceSummary: LABELS[preference], analysisSource: 'algorithm', model: null, availabilityToken, generatedAt: now() })
}

module.exports = { handle, recommend, infer }
