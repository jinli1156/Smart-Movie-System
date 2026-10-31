'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const { at } = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/core')
const expiry = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/order-expiry')
const orders = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/orders')

function database(seed) {
  const tables = Object.fromEntries(Object.entries(seed).map(([name, rows]) => [name, new Map(rows.map(row => [row._id, row]))]))
  const collection = name => {
    const rows = () => [...(tables[name] || new Map()).values()]
    const create = (filter = {}, offset = 0, size = 1000) => {
      return {
        where(next) { return create(next, offset, size) },
        limit(next) { return create(filter, offset, next) },
        skip(next) { return create(filter, next, size) },
        orderBy() { return create(filter, offset, size) },
        async count() { return { total: rows().filter(row => Object.entries(filter).every(([key, value]) => value?.lt !== undefined ? row[key] < value.lt : row[key] === value)).length } },
        async get() { return { data: rows().filter(row => Object.entries(filter).every(([key, value]) => value?.lt !== undefined ? row[key] < value.lt : row[key] === value)).slice(offset, offset + size) } }
      }
    }
    return {
      ...create(),
      doc(id) {
        return {
          async get() { return { data: tables[name]?.get(id) } },
          async update(patch) { Object.assign(tables[name].get(id), patch) }
        }
      }
    }
  }
  const db = { command: { lt(value) { return { lt: value } } }, collection,
    async startTransaction() { return { collection, async commit() {}, async rollback() {} } } }
  return { db, tables }
}

test('an order-list request cancels the caller’s expired order and releases its own seat', async () => {
  const created = at(Date.now() - 20 * 60000)
  const old = { _id: 'order:1', id: 1, orderNo: 'A', userId: 1, status: 0, createTime: created, showtimeId: 2, seats: '1排1座' }
  const other = { _id: 'order:2', id: 2, orderNo: 'B', userId: 2, status: 0, createTime: created, showtimeId: 2, seats: '1排2座' }
  const ownHold = { _id: '2:1排1座', orderNo: 'A', status: 'pending' }
  const otherHold = { _id: '2:1排2座', orderNo: 'B', status: 'pending' }
  const { db } = database({
    mts_order: [old, other], mts_seat_hold: [ownHold, otherHold],
    mts_showtime: [{ _id: 'show:2', id: 2, movieId: 3, cinemaId: 4, hallId: 5, startTime: at(Date.now() + 3600000) }],
    mts_movie: [{ _id: 'movie:3', id: 3, title: '影片' }],
    mts_cinema: [{ _id: 'cinema:4', id: 4, name: '影院' }],
    mts_hall: [{ _id: 'hall:5', id: 5, name: '一厅' }],
    mts_user: [{ _id: 'user:1', id: 1, username: 'alice' }]
  })
  const result = await orders.handle({ method: 'GET', path: '/order/my', user: { id: 1, role: 'USER' }, query: { page: 1, size: 10 } }, db)
  assert.equal(result.data.records[0].status, 2)
  assert.equal(old.status, 2)
  assert.equal(ownHold.status, 'released')
  assert.equal(other.status, 0)
  assert.equal(otherHold.status, 'pending')
})

test('request cleanup does not release a seat reassigned to another order', async () => {
  const clock = Date.now()
  const old = { _id: 'order:1', orderNo: 'A', userId: 1, status: 0, createTime: at(clock - 20 * 60000), showtimeId: 2, seats: '1排1座' }
  const hold = { _id: '2:1排1座', orderNo: 'NEW', status: 'pending' }
  const { db } = database({ mts_order: [old], mts_seat_hold: [hold] })
  assert.equal(await expiry.cancelExpiredOrder(db, old, clock), true)
  assert.equal(old.status, 2)
  assert.equal(hold.status, 'pending')
  assert.equal(await expiry.cancelExpiredOrder(db, old, clock), false)
})
