'use strict'

const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('fs')
const path = require('path')
const assistantTools = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/assistant-tools')
const watch = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/assistant-plan')
const scheduling = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/scheduling-intelligence')

const local = path.join(__dirname, '..', '.local')
const snapshots = fs.existsSync(local) ? fs.readdirSync(local).filter(name => name.startsWith('mysql-export-') && !fs.existsSync(path.join(local, name, 'INVALID.txt')) && !fs.existsSync(path.join(local, name, 'SUPERSEDED.txt'))).sort() : []
const snapshot = snapshots.at(-1)

function load() {
  const root = path.join(local, snapshot)
  const names = Object.keys(JSON.parse(fs.readFileSync(path.join(root, 'manifest.json'), 'utf8')).collections)
  return Object.fromEntries(names.map(name => {
    const content = fs.readFileSync(path.join(root, `${name}.json`), 'utf8').trim()
    return [name, content ? content.split('\n').map(JSON.parse) : []]
  }))
}

function memoryDb(tables) {
  const expression = test => ({ test, and(other) { return expression(value => test(value) && other.test(value)) } })
  const matches = (filter, item) => filter.matchesDoc ? filter.matchesDoc(item) : Object.entries(filter).every(([key, value]) => value && typeof value.test === 'function' ? value.test(item[key]) : item[key] === value)
  const command = { gte: value => expression(item => item >= value), lt: value => expression(item => item < value), in: values => expression(item => values.includes(item)), and: conditions => ({ matchesDoc: item => conditions.every(condition => matches(condition, item)) }), or: conditions => ({ matchesDoc: item => conditions.some(condition => matches(condition, item)) }) }
  return { command, collection(name) {
    let filter = {}, offset = 0, count = 1000
    const query = {
      where(value) { filter = value; return query }, skip(value) { offset = value; return query }, limit(value) { count = value; return query }, orderBy() { return query },
      async count() { return { total: (tables[name] || []).filter(item => matches(filter, item)).length } },
      async get() { return { data: (tables[name] || []).filter(item => matches(filter, item)).slice(offset, offset + count) } }
    }
    return query
  } }
}

test('assistant searches real movie actors and cinema addresses', { skip: !snapshot }, async () => {
  const tables = load()
  const db = memoryDb(tables)
  const movie = tables.mts_movie.find(item => item.deleted === 0 && item.actors && !item.title.includes(item.actors.split(',')[0]))
  const actor = movie.actors.split(',')[0]
  const movies = await assistantTools.execute('search_movies', JSON.stringify({ keyword: actor }), { user: null }, db, watch.fromSaved({ criteria: {} }))
  assert.equal(movies.result.ok, true, movies.result.message)
  assert(movies.result.data.items.some(item => item.id === movie.id))
  const cinema = tables.mts_cinema.find(item => item.deleted === 0 && item.status === 1 && item.address && !item.name.includes(item.address.slice(0, 4)))
  const cinemas = await assistantTools.execute('search_cinemas', JSON.stringify({ keyword: cinema.address.slice(0, 4) }), { user: null }, db, watch.fromSaved({ criteria: {} }))
  assert.equal(cinemas.result.ok, true, cinemas.result.message)
  assert(cinemas.result.data.items.some(item => item.id === cinema.id))
})

test('exported business data supports assistant showtime lookup and scheduling forecast', { skip: !snapshot }, async t => {
  const tables = load()
  const available = tables.mts_showtime.find(show => show.status === 0 && show.deleted === 0 && Date.parse(show.startTime) > Date.now() + 3600000)
  if (!available) return t.skip('快照没有未来场次，需用新快照验证预测')
  const db = memoryDb(tables)
  const plan = watch.fromSaved({ criteria: { date: available.startTime.slice(0, 10), people: 2 }, options: [] })
  const queried = await assistantTools.execute('search_showtimes', JSON.stringify({ date: available.startTime.slice(0, 10), movieId: available.movieId, cinemaId: available.cinemaId, people: 2 }), { user: null }, db, plan)
  assert.equal(queried.result.ok, true, queried.result.message)
  assert(queried.result.data.total >= 1)
  assert(queried.result.data.items.every(item => item.movieId === available.movieId && item.cinemaId === available.cinemaId && item.availableSeats >= 2))
  const forecast = await scheduling.handle({ method: 'POST', path: '/statistics/scheduling/predict', body: { cinemaId: available.cinemaId, movieId: available.movieId, hallId: available.hallId, startTime: available.startTime }, user: { id: 1, role: 'ADMIN' } }, db)
  assert(forecast.data.predictedRate >= 5 && forecast.data.predictedRate <= 95)
  const insights = await scheduling.handle({ method: 'GET', path: '/statistics/scheduling-insights', query: { cinemaId: available.cinemaId }, user: { id: 1, role: 'ADMIN' } }, db)
  assert(insights.data.summary.historicalSamples >= 0)
  const proposal = await scheduling.optimize(db, { cinemaId: available.cinemaId, targetDate: available.startTime.slice(0, 10), targetShowtimesPerHall: 3, maxShowtimesPerMovie: 20, minDistinctMovies: 1, minGoldenShowtimesPerHall: 0, cleaningMinutes: 20, revenueWeight: 50, occupancyWeight: 35, diversityWeight: 15 })
  assert(['FEASIBLE', 'INFEASIBLE'].includes(proposal.status))
  if (proposal.status === 'FEASIBLE') assert(proposal.selectedShowtimes.length > 0)
})
