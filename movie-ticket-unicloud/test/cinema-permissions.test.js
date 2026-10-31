'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const path = '../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/'
const core = require(path + 'core')
const permissions = require(path + 'permissions')
const orders = require(path + 'orders')
const catalog = require(path + 'catalog')
const auth = require(path + 'auth')
const statistics = require(path + 'statistics')
const scheduling = require(path + 'scheduling')
const intelligence = require(path + 'scheduling-intelligence')
const recommend = require(path + 'recommend')
const api = require(path + 'index')

function database(initial = {}) {
  const tables = structuredClone(initial)
  const writes = []
  const matches = (row, filter) => Object.entries(filter).every(([key, value]) =>
    value instanceof RegExp ? value.test(row[key] || '')
      : value?.op === 'in' ? value.value.includes(row[key])
      : value?.op === 'lt' ? row[key] < value.value
      : row[key] === value)
  function collection(name, transaction = false) {
    tables[name] ||= []
    let filter = {}, offset = 0, size = 1000, sorting = null
    const matching = () => tables[name].filter(row => matches(row, filter))
    const query = {
      where(value) { filter = value; return query },
      skip(value) { offset = value; return query },
      limit(value) { size = value; return query },
      orderBy(field, direction) { sorting = { field, direction }; return query },
      async count() { return { total: matching().length } },
      async get() {
        let rows = matching()
        if (sorting) rows = rows.slice().sort((a, b) => String(a[sorting.field]).localeCompare(String(b[sorting.field])) * (sorting.direction === 'desc' ? -1 : 1))
        return { data: structuredClone(rows.slice(offset, offset + size)) }
      },
      async update(data) { const rows = matching(); rows.forEach(row => Object.assign(row, data)); writes.push({ name, data }); return { updated: rows.length } },
      doc(id) {
        return {
          async get() { const row = tables[name].find(row => row._id === id); return { data: transaction ? structuredClone(row) : (row ? [structuredClone(row)] : []) } },
          async update(data) { const row = tables[name].find(row => row._id === id); assert(row); Object.assign(row, data); writes.push({ name, id, data }); return { updated: 1 } },
          async remove() { tables[name] = tables[name].filter(row => row._id !== id); writes.push({ name, id, removed: true }) }
        }
      }
    }
    return query
  }
  return { tables, writes, collection,
    command: { in: value => ({ op: 'in', value }), lt: value => ({ op: 'lt', value }) },
    async startTransaction() { return { collection: name => collection(name, true), async commit() {}, async rollback() {} } }
  }
}
const manager = { id: 3, role: 'CINEMA_ADMIN', cinemaId: 10 }
const admin = { id: 1, role: 'ADMIN' }
const buyer = { id: 2, role: 'USER' }
const request = (path, user = manager, method = 'GET', body, query = {}) => ({ path, user, method, body, query })
const denied = action => assert.rejects(action, error => error.code === 403)
function fixture() {
  const time = core.now()
  return database({
    mts_user: [{ _id: 'u1', ...admin, username: 'admin', status: 1, deleted: 0 }, { _id: 'u2', ...buyer, username: 'buyer', status: 1, deleted: 0 }, { _id: 'u3', ...manager, username: 'manager', status: 1, deleted: 0, points: 80 }],
    mts_cinema: [10, 20].map(id => ({ _id: `c${id}`, id, name: `影院${id}`, status: 1, deleted: 0 })),
    mts_hall: [10, 20].map(id => ({ _id: `h${id}`, id, cinemaId: id, name: '一号厅', status: 1, deleted: 0 })),
    mts_movie: [{ _id: 'm1', id: 1, title: '电影', status: 1, deleted: 0 }],
    mts_showtime: [10, 20].map(id => ({ _id: `s${id}`, id, movieId: 1, cinemaId: id, hallId: id, status: 0, deleted: 0, totalSeats: 100, startTime: '2099-01-01T10:00:00+08:00' })),
    mts_order: [10, 20].map(id => ({ _id: `o${id}`, id, orderNo: `ORDER-${id}`, showtimeId: id, userId: 2, ticketCode: String(id).repeat(4), status: 1, isPicked: 0, totalPrice: id, seatCount: 1, createTime: time, payTime: time }))
  })
}

test('cloud manager order pagination and search cannot include another cinema or leak totals', async () => {
  const db = fixture()
  const result = await orders.handle(request('/order/page'), db)
  assert.equal(result.data.total, 1)
  assert.deepEqual(result.data.records.map(item => item.orderNo), ['ORDER-10'])
  assert.equal((await orders.handle(request('/order/page', manager, 'GET', null, { orderNo: 'ORDER-20' }), db)).data.total, 0)
  assert.equal((await orders.handle(request('/order/page', admin), db)).data.total, 2)
  assert.equal((await orders.handle(request('/order/my', buyer), db)).data.total, 2)
})

test('unassigned and empty cinemas fail closed without falling back to all orders', async () => {
  const db = fixture()
  await denied(() => orders.handle(request('/order/page', { ...manager, cinemaId: null }), db))
  const empty = await orders.handle(request('/order/page', { ...manager, cinemaId: 30 }), db)
  assert.equal(empty.data.total, 0)
  assert.deepEqual(empty.data.records, [])
})

test('order details, cancellation, refunds and ticket codes all check owner or assigned cinema before writes', async () => {
  const db = fixture()
  for (const user of [manager, { id: 9, role: 'USER' }]) {
    await denied(() => orders.handle(request('/order/ORDER-20', user), db))
    for (const action of ['cancel', 'refund']) await denied(() => orders.handle(request(`/order/ORDER-20/${action}`, user, 'POST'), db))
    await denied(() => orders.handle(request('/order/pick', user, 'POST', null, { ticketCode: '20202020' }), db))
  }
  assert.equal(db.writes.length, 0)
  assert.equal((await orders.handle(request('/order/ORDER-20', buyer), db)).code, 200)
  assert.equal((await orders.handle(request('/order/ORDER-20', admin), db)).code, 200)
  assert.equal((await orders.handle(request('/order/pick', manager, 'POST', null, { ticketCode: '10101010' }), db)).data.isPicked, 1)
  assert.equal((await orders.handle(request('/order/pick', buyer, 'POST', null, { ticketCode: '20202020' }), db)).data.isPicked, 1)
})

test('catalog guards original and destination cinemas and reserves global movie writes to platform admins', async () => {
  const db = fixture()
  for (const type of ['hall', 'showtime']) {
    await denied(() => catalog.handle(request(`/${type}/20`, manager, 'PUT', { cinemaId: 10 }), db))
    await denied(() => catalog.handle(request(`/${type}/10`, manager, 'PUT', { cinemaId: 20 }), db))
    await denied(() => catalog.handle(request(`/${type}/20`, manager, 'DELETE'), db))
    await denied(() => catalog.handle(request(`/${type}/20/status`, manager, 'PUT', null, { status: 0 }), db))
    await denied(() => catalog.handle(request(`/${type}`, manager, 'POST', { cinemaId: 20 }), db))
    await denied(() => catalog.handle(request(`/${type}/page`, manager, 'GET', null, { cinemaId: 20 }), db))
    assert.equal((await catalog.handle(request(`/${type}/page`), db)).data.total, 1)
  }
  await denied(() => catalog.handle(request('/movie', manager, 'POST', { title: 'x', duration: 120 }), db))
  await denied(() => catalog.handle(request('/movie/1/status', manager, 'PUT', null, { status: 0 }), db))
  await denied(() => catalog.handle(request('/cinema', manager, 'POST', { name: 'x' }), db))
  assert.equal(db.writes.length, 0)
  await catalog.handle(request('/hall/10', manager, 'PUT', { name: '新厅名' }), db)
  assert.equal(db.tables.mts_hall[0].name, '新厅名')
  assert.equal((await catalog.handle(request('/hall/cinema/20', null), db)).data.length, 1)
})

test('private dashboard, ranking and revenue aggregate only assigned cinema, public box office remains public', async () => {
  const db = fixture()
  const dashboard = (await statistics.handle(request('/statistics/dashboard'), db)).data
  assert.equal(dashboard.todayRevenue, 10)
  assert.equal(dashboard.todayOrderCount, 1)
  assert.equal(dashboard.cinemaCount, 1)
  assert.equal((await statistics.handle(request('/statistics/box-office'), db)).data[0].boxOffice, 10)
  assert.equal((await statistics.handle(request('/movie/box-office/today', null), db)).data[0].boxOffice, 30)
  const date = core.now().slice(0, 10)
  assert.equal((await statistics.handle(request('/statistics/revenue', manager, 'GET', null, { startDate: date, endDate: date }), db)).data[0].revenue, 10)
  await denied(() => statistics.handle(request('/statistics/showtime/20'), db))
  await denied(() => statistics.handle(request('/statistics/dashboard', { ...manager, cinemaId: null }), db))
})

test('foreign workspaces, draft writes and intelligence are denied before running calculations', async () => {
  const db = fixture()
  await denied(() => scheduling.handle(request('/statistics/scheduling/workspace', manager, 'GET', null, { cinemaId: 20 }), db))
  for (const route of ['check', 'apply']) await denied(() => scheduling.handle(request(`/statistics/scheduling/${route}`, manager, 'POST', { cinemaId: 20 }), db))
  await denied(() => scheduling.handle(request('/statistics/scheduling/saved-drafts', manager, 'PUT', { cinemaId: 20 }), db))
  for (const route of ['predict', 'optimize']) await denied(() => intelligence.handle(request(`/statistics/scheduling/${route}`, manager, 'POST', { cinemaId: 20 }), db))
  await denied(() => intelligence.handle(request('/statistics/scheduling-insights', manager, 'GET', null, { cinemaId: 20 }), db))
  await denied(() => intelligence.handle(request('/statistics/scheduling/simulate', manager, 'POST', { originalShowtimeId: 20 }), db))
  assert.equal(db.writes.length, 0)
})

test('reassignment hides saved drafts from the former cinema and prevents retrieval or removal', async () => {
  const db = fixture()
  const id = '12345678-1234-1234-1234-123456789012'
  db.tables.mts_scheduling_draft = [{ _id: 'draft', id, userId: 3, cinemaId: 20, content: { secret: 'foreign' } }]
  assert.deepEqual((await scheduling.handle(request('/statistics/scheduling/saved-drafts'), db)).data, [])
  await denied(() => scheduling.handle(request(`/statistics/scheduling/saved-drafts/${id}`), db))
  await denied(() => scheduling.handle(request(`/statistics/scheduling/saved-drafts/${id}`, manager, 'DELETE'), db))
  assert.equal(db.writes.length, 0)
})

test('only platform admins can assign a valid cinema and account edits cannot overwrite password or points', async () => {
  const db = fixture()
  await denied(() => auth.handle(request('/user/page'), db))
  await denied(() => recommend.handle(request('/recommend/evaluation'), db))
  await assert.rejects(() => auth.handle(request('/user/3', admin, 'PUT', { role: 'CINEMA_ADMIN' }), db), error => error.code === 400)
  await assert.rejects(() => auth.handle(request('/user/3', admin, 'PUT', { role: 'CINEMA_ADMIN', cinemaId: 999 }), db), error => error.code === 400)
  await assert.rejects(() => auth.handle(request('/user/3', admin, 'PUT', { role: 'SUPERUSER' }), db), error => error.code === 400)
  await auth.handle(request('/user/3', admin, 'PUT', { role: 'CINEMA_ADMIN', cinemaId: 20, password: 'unexpected', points: 999 }), db)
  assert.equal(db.tables.mts_user[2].cinemaId, 20)
  assert.equal(db.tables.mts_user[2].points, 80)
  assert.equal(db.tables.mts_user[2].password, undefined)
  await auth.handle(request('/user/3', admin, 'PUT', { role: 'USER', cinemaId: 20 }), db)
  assert.equal(db.tables.mts_user[2].cinemaId, null)
})

test('HTTP entry refreshes role, cinema and enabled status for every request using an existing token', async () => {
  const db = fixture()
  const previous = process.env.JWT_SECRET
  const previousCloud = global.uniCloud
  process.env.JWT_SECRET = 'local-permission-tests-only-not-a-deployment-secret'
  global.uniCloud = { database: () => db }
  try {
    const token = core.signToken(manager)
    const call = async route => JSON.parse((await api.main({ httpMethod: 'GET', path: route, headers: { authorization: `Bearer ${token}` } })).body)
    assert.equal((await call('/order/ORDER-10')).code, 200)
    db.tables.mts_user[2].cinemaId = 20
    assert.equal((await call('/order/ORDER-10')).code, 403)
    assert.equal((await call('/order/ORDER-20')).code, 200)
    db.tables.mts_user[2].role = 'USER'
    assert.equal((await call('/order/page')).code, 403)
    db.tables.mts_user[2].status = 0
    assert.equal((await call('/order/my')).code, 401)
  } finally {
    if (previous === undefined) delete process.env.JWT_SECRET
    else process.env.JWT_SECRET = previous
    global.uniCloud = previousCloud
  }
})

test('cinema scope collects showtime IDs across database pages without truncating history', async () => {
  const db = database({ mts_showtime: Array.from({ length: 1001 }, (_, index) => ({ id: index + 1, cinemaId: 10 })) })
  const result = await permissions.orderFilter(request('/order/page'), db)
  assert.equal(result.showtimeId.value.length, 1001)
  assert.equal(result.showtimeId.value.at(-1), 1001)
})
