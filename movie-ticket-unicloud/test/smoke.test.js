'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const core = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/core')
const catalog = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/catalog')
const orders = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/orders')
const pricing = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/pricing')
const recommend = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/recommend')
const uploads = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/uploads')
const seats = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/seats')
const schedulingIntelligence = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/scheduling-intelligence')
const assistantPlan = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/assistant-plan')
const assistantTools = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/assistant-tools')
const maintenance = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-maintenance')

function memoryDb(tables) {
  return {
    collection(name) {
      let filter = {}, offset = 0, count = 1000
      const query = {
        where(value) { filter = value; return query },
        skip(value) { offset = value; return query },
        limit(value) { count = value; return query },
        async get() { return { data: (tables[name] || []).filter(item => Object.entries(filter).every(([key, value]) => item[key] === value)).slice(offset, offset + count) } }
      }
      return query
    }
  }
}

test('URLized HTTP event parses JSON and query without touching credentials', () => {
  const request = core.parseEvent({ httpMethod: 'POST', path: '/movie', headers: { 'Content-Type': 'application/json' }, body: '{"title":"测试"}', queryStringParameters: { page: '2' } })
  assert.equal(request.body.title, '测试')
  assert.equal(request.query.page, '2')
  assert.equal(core.parseEvent({ httpMethod: 'POST', path: '/movie', headers: { 'Content-Type': 'application/json' }, body: '{"title":"测试"}', isBase64Encoded: 'false' }).body.title, '测试')
})

test('token is signed and tampering is rejected', () => {
  const previous = process.env.JWT_SECRET
  process.env.JWT_SECRET = 'test-only-secret-with-at-least-thirty-two-characters'
  try {
    const token = core.signToken({ id: 42, role: 'USER' })
    assert.deepEqual(core.verifyToken(`Bearer ${token}`), { id: 42, role: 'USER' })
    const [header, claims, signature] = token.split('.')
    const changed = `${header}.${Buffer.from(JSON.stringify({ sub: '43', role: 'USER', exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url')}.${signature}`
    assert.equal(core.verifyToken(`Bearer ${changed}`), null)
  } finally {
    if (previous === undefined) delete process.env.JWT_SECRET
    else process.env.JWT_SECRET = previous
  }
})

test('catalog write is denied before database access', async () => {
  await assert.rejects(() => catalog.handle({ method: 'POST', path: '/movie', body: { title: 'x' }, user: null }, {}), error => error.code === 401)
})

test('seat validation respects hall layout and bounds', () => {
  const hall = { totalRows: 2, totalCols: 2, seatLayout: JSON.stringify({ seats: [[1, 0], [1, 1]] }) }
  assert.equal(orders.seatEnabled(hall, '1排1座'), true)
  assert.equal(orders.seatEnabled(hall, '1排2座'), false)
  assert.equal(orders.seatEnabled(hall, '3排1座'), false)
  assert.equal(orders.seatEnabled(hall, 'abc'), false)
})

test('price plans respect one coupon and 100 points per order', () => {
  const result = pricing.plans(80, 500, [{ id: 1, name: '满80减6.99', amount: 6.99, minSpend: 80 }], true)
  assert.equal(result[0].couponId, 1)
  assert.equal(result[0].pointsUsed, 100)
  assert.equal(result[0].totalPrice, 72.01)
})

test('recommendation ranks a preferred genre ahead of unrelated movies', () => {
  const ranked = recommend.rank([{ id: 1, title: 'A', genre: '喜剧', rating: 8 }, { id: 2, title: 'B', genre: '动作', rating: 8 }], [], ['喜剧'])
  assert.equal(ranked[0].id, 1)
})

test('image upload requires administrator before parsing bytes', async () => {
  await assert.rejects(() => uploads.handle({ path: '/upload/image', method: 'POST', user: null }), error => error.code === 401)
})

test('seat recommendations never include occupied or disabled seats', () => {
  const hall = { totalRows: 2, totalCols: 3, seatLayout: JSON.stringify({ seats: [[1, 1, 0], [1, 1, 1]] }) }
  const choices = seats.recommend(hall, { basePrice: 50 }, new Set(['1排1座']), 2, 'CENTER', 5)
  assert(choices.length > 0)
  for (const choice of choices) {
    assert(!choice.seats.includes('1排1座'))
    assert(!choice.seats.includes('1排3座'))
  }
})

test('scheduling heuristic keeps golden time and movie diversity constraints', async () => {
  const db = memoryDb({
    mts_cinema: [{ id: 1, deleted: 0 }],
    mts_hall: [{ id: 10, cinemaId: 1, status: 1, deleted: 0, name: '一厅', totalSeats: 80 }],
    mts_movie: [
      { id: 1, title: '甲', duration: 120, rating: 8, status: 1, deleted: 0 },
      { id: 2, title: '乙', duration: 120, rating: 7, status: 1, deleted: 0 },
      { id: 3, title: '丙', duration: 120, rating: 6, status: 1, deleted: 0 }
    ]
  })
  const targetDate = new Date(Date.now() + 4 * 86400000).toISOString().slice(0, 10)
  const result = await schedulingIntelligence.optimize(db, { cinemaId: 1, targetDate, targetShowtimesPerHall: 5, maxShowtimesPerMovie: 3, minDistinctMovies: 3, minGoldenShowtimesPerHall: 1, cleaningMinutes: 20, revenueWeight: 50, occupancyWeight: 35, diversityWeight: 15 })
  assert.equal(result.status, 'FEASIBLE')
  assert.equal(result.selectedShowtimes.length, 5)
  assert.equal(new Set(result.selectedShowtimes.map(item => item.movieId)).size, 3)
  assert(result.selectedShowtimes.some(item => Number(item.startTime.slice(11, 13)) >= 18 && Number(item.startTime.slice(11, 13)) < 22))
  const rows = result.selectedShowtimes.slice().sort((a, b) => a.startTime.localeCompare(b.startTime))
  for (let i = 1; i < rows.length; i++) assert(Date.parse(rows[i].startTime) >= Date.parse(rows[i - 1].endTime) + 20 * 60000)
})

test('scheduling insights handle cinemas without halls', async () => {
  const db = memoryDb({ mts_cinema: [{ id: 1, deleted: 0 }], mts_movie: [{ id: 1, title: '甲', status: 1, deleted: 0 }] })
  const result = await schedulingIntelligence.handle({ method: 'GET', path: '/statistics/scheduling-insights', query: { cinemaId: '1' }, user: { id: 1, role: 'ADMIN' } }, db)
  assert.equal(result.data.summary.futureShowtimeCount, 0)
})

test('assistant watch plan patches one condition without losing others', () => {
  const first = assistantPlan.fromSaved({ criteria: { date: '2026-09-29', movieId: 3, showtimeId: 9, people: 2 }, options: [] }, { excludedGenres: ['恐怖'] })
  const next = assistantPlan.patch(first, { movieId: 5 })
  assert.deepEqual(next.criteria, { date: '2026-09-29', movieId: 5, people: 2, excludedGenres: ['恐怖'] })
  assert.throws(() => assistantPlan.patch(next, { people: 6 }), error => error.code === 400)
  assert.throws(() => assistantPlan.patch(next, { date: '2026-99-99' }), error => error.code === 400)
})

test('assistant tools keep order data private and accept typed watch plan changes', async () => {
  const plan = assistantPlan.fromSaved({ criteria: { people: 2 }, options: [] })
  const denied = await assistantTools.execute('get_my_orders', '{}', { user: null }, {}, plan)
  assert.equal(denied.result.code, 'LOGIN_REQUIRED')
  const changed = await assistantTools.execute('update_watch_plan', JSON.stringify({ excludedGenres: ['恐怖'], date: '2026-09-29' }), { user: null }, {}, plan)
  assert.equal(changed.result.ok, true)
  assert.deepEqual(changed.plan.criteria, { people: 2, excludedGenres: ['恐怖'], date: '2026-09-29' })
  const rules = await assistantTools.execute('search_knowledge', JSON.stringify({ query: '退票手续费' }), { user: null }, {}, plan)
  assert.equal(rules.result.ok, true)
  assert.equal(rules.result.data[0].id, 'refund')
})

test('expired pending order is cancelled and only its own seat hold is released', async () => {
  const clock = Date.parse('2026-09-25T12:00:00+08:00')
  const order = { _id: 'order:1', orderNo: 'A', status: 0, createTime: '2026-09-25T11:40:00+08:00', showtimeId: 2, seats: '1排1座' }
  const hold = { _id: '2:1排1座', orderNo: 'A', status: 'pending' }
  const tables = { mts_order: new Map([[order._id, order]]), mts_seat_hold: new Map([[hold._id, hold]]) }
  const collection = name => ({
    where(filter) { return { limit() { return { async get() { return { data: [...tables[name].values()].filter(item => item.status === filter.status && item.createTime < filter.createTime.value) } } } } } },
    doc(id) { return { async get() { return { data: tables[name].get(id) } }, async update(patch) { Object.assign(tables[name].get(id), patch) } } }
  })
  const db = { command: { lt(value) { return { value } } }, collection, async startTransaction() { return { collection, async commit() {}, async rollback() {} } } }
  const result = await maintenance.cancelExpired(db, clock)
  assert.equal(result.cancelled, 1)
  assert.equal(order.status, 2)
  assert.equal(hold.status, 'released')
  const second = await maintenance.cancelExpired(db, clock)
  assert.equal(second.cancelled, 0)
})
