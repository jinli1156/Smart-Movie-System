'use strict'

const { ApiError, assert, success, numericId, pageArgs, pageResult, now, clean, nextId, requireUser } = require('./core')
const { scopeCinemaId, requireCinema } = require('./permissions')

const TYPES = {
  movie: { collection: 'mts_movie', fields: ['title', 'englishTitle', 'director', 'actors', 'genre', 'country', 'language', 'duration', 'releaseDate', 'poster', 'trailer', 'description', 'rating', 'status'], required: ['title', 'duration'] },
  cinema: { collection: 'mts_cinema', fields: ['name', 'address', 'lng', 'lat', 'phone', 'description', 'images', 'status'], required: ['name', 'address'] },
  hall: { collection: 'mts_hall', fields: ['cinemaId', 'name', 'totalRows', 'totalCols', 'totalSeats', 'seatLayout', 'status'], required: ['cinemaId', 'name', 'totalRows', 'totalCols'] },
  showtime: { collection: 'mts_showtime', fields: ['movieId', 'cinemaId', 'hallId', 'startTime', 'endTime', 'basePrice', 'totalSeats', 'availableSeats', 'status'], required: ['movieId', 'cinemaId', 'hallId', 'startTime', 'endTime', 'basePrice'] }
}

function pick(body, fields) {
  assert(body && typeof body === 'object' && !Array.isArray(body), 400, '请求参数无效')
  return Object.fromEntries(fields.filter(key => Object.prototype.hasOwnProperty.call(body, key)).map(key => [key, body[key]]))
}

async function one(db, type, id) {
  const result = await db.collection(TYPES[type].collection).where({ id }).limit(1).get()
  assert(result.data && result.data.length && result.data[0].deleted !== 1, 404, '记录不存在')
  return result.data[0]
}

async function decorate(db, type, docs) {
  if (!docs.length || type === 'movie' || type === 'cinema') return docs.map(clean)
  const cinemaIds = [...new Set(docs.map(doc => doc.cinemaId).filter(Boolean))]
  const cinemaResult = cinemaIds.length ? await db.collection('mts_cinema').where({ id: db.command.in(cinemaIds) }).get() : { data: [] }
  const cinemas = new Map(cinemaResult.data.map(item => [item.id, item]))
  if (type === 'hall') return docs.map(doc => ({ ...clean(doc), cinemaName: cinemas.get(doc.cinemaId)?.name || '' }))
  const movieIds = [...new Set(docs.map(doc => doc.movieId).filter(Boolean))]
  const hallIds = [...new Set(docs.map(doc => doc.hallId).filter(Boolean))]
  const [movieResult, hallResult] = await Promise.all([
    movieIds.length ? db.collection('mts_movie').where({ id: db.command.in(movieIds) }).get() : { data: [] },
    hallIds.length ? db.collection('mts_hall').where({ id: db.command.in(hallIds) }).get() : { data: [] }
  ])
  const movies = new Map(movieResult.data.map(item => [item.id, item]))
  const halls = new Map(hallResult.data.map(item => [item.id, item]))
  const holdsResult = await db.collection('mts_seat_hold').where({ showtimeId: db.command.in(docs.map(item => item.id)) }).limit(1000).get()
  const holdCounts = new Map()
  for (const hold of holdsResult.data || []) {
    if (hold.status === 'paid' || (hold.status === 'pending' && hold.expiresAt > Date.now())) {
      holdCounts.set(hold.showtimeId, (holdCounts.get(hold.showtimeId) || 0) + 1)
    }
  }
  return docs.map(doc => ({
    ...clean(doc), availableSeats: Math.max(0, Number(doc.totalSeats || halls.get(doc.hallId)?.totalSeats || 0) - (holdCounts.get(doc.id) || 0)), cinemaName: cinemas.get(doc.cinemaId)?.name || '',
    movieTitle: movies.get(doc.movieId)?.title || '', moviePoster: movies.get(doc.movieId)?.poster || '',
    movieDuration: movies.get(doc.movieId)?.duration || 0, hallName: halls.get(doc.hallId)?.name || ''
  }))
}

async function list(db, type, query, all = false, request = {}) {
  const { current, size } = pageArgs(query)
  const filter = { deleted: 0 }
  for (const key of ['status', 'movieId', 'cinemaId']) {
    if (query[key] !== undefined && query[key] !== '') filter[key] = Number(query[key])
  }
  if (type !== 'movie') {
    const scope = scopeCinemaId(request, type === 'cinema' ? null : query.cinemaId)
    if (scope != null) filter[type === 'cinema' ? 'id' : 'cinemaId'] = scope
  }
  if (type === 'movie' && query.keyword) filter.title = new RegExp(String(query.keyword).replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i')
  if (type === 'cinema' && query.keyword) filter.name = new RegExp(String(query.keyword).replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i')
  if (type === 'showtime' && query.date) {
    filter.startTime = db.command.gte(`${query.date}T00:00:00`).and(db.command.lt(`${query.date}T23:59:59.999`))
  }
  let collection = db.collection(TYPES[type].collection)
  if (Object.keys(filter).length) collection = collection.where(filter)
  const count = await collection.count()
  // list endpoints are bounded; page endpoints preserve MyBatis-Plus response shape.
  const readSize = all ? Math.min(count.total, 1000) : size
  const result = await collection.orderBy('id', 'asc').skip(all ? 0 : (current - 1) * size).limit(readSize).get()
  const records = await decorate(db, type, result.data || [])
  return all ? records : pageResult(records, count.total, current, size)
}

async function handle(request, db) {
  const { method, path, query, body } = request
  const segments = path.split('/').filter(Boolean)
  const type = segments[0]
  if (!TYPES[type]) throw new ApiError(404, '接口不存在')
  const spec = TYPES[type]

  if (method === 'GET' && segments[1] === 'page' && segments.length === 2) return success(await list(db, type, query, false, request))
  if (method === 'GET' && type === 'movie' && ['now-showing', 'coming-soon'].includes(segments[1]) && segments.length === 2) {
    return success(await list(db, type, { status: segments[1] === 'now-showing' ? 1 : 2 }, true))
  }
  if (method === 'GET' && type === 'cinema' && segments[1] === 'all') return success(await list(db, type, { status: 1 }, true, request))
  if (method === 'GET' && type === 'showtime' && segments[1] === 'list') return success(await list(db, type, query, true))
  if (method === 'GET' && type === 'hall' && segments[1] === 'cinema' && segments[2]) return success(await list(db, type, { cinemaId: numericId(segments[2]) }, true))
  if (method === 'GET' && segments.length === 2 && /^\d+$/.test(segments[1])) {
    const doc = await one(db, type, numericId(segments[1]))
    return success((await decorate(db, type, [doc]))[0])
  }
  if (method === 'POST' && segments.length === 1) {
    requireUser(request, type === 'movie' || type === 'cinema' ? ['ADMIN'] : ['ADMIN', 'CINEMA_ADMIN'])
    const data = pick(body, spec.fields)
    if (type === 'hall' || type === 'showtime') {
      requireCinema(request, data.cinemaId)
      await one(db, 'cinema', numericId(data.cinemaId))
    }
    for (const field of spec.required) assert(data[field] !== undefined && data[field] !== null && data[field] !== '', 400, `${field}不能为空`)
    if (type === 'hall') data.totalSeats = Number(data.totalRows) * Number(data.totalCols)
    if (type === 'showtime') {
      const hall = await one(db, 'hall', numericId(data.hallId))
      assert(hall.cinemaId === numericId(data.cinemaId), 400, '影厅不属于该影院')
      await one(db, 'movie', numericId(data.movieId))
      data.totalSeats = hall.totalSeats
      data.availableSeats = hall.totalSeats
    }
    data.id = await nextId(db, type)
    data.deleted = 0
    data.createTime = data.updateTime = now()
    await db.collection(spec.collection).add(data)
    return success(null, '创建成功')
  }
  if (segments.length === 2 && /^\d+$/.test(segments[1])) {
    const id = numericId(segments[1])
    if (method === 'PUT') {
      requireUser(request, type === 'movie' ? ['ADMIN'] : ['ADMIN', 'CINEMA_ADMIN'])
      const existing = await one(db, type, id)
      if (type !== 'movie') requireCinema(request, type === 'cinema' ? existing.id : existing.cinemaId)
      const data = pick(body, spec.fields)
      assert(Object.keys(data).length, 400, '没有可更新的字段')
      if (type === 'hall' || type === 'showtime') {
        const cinemaId = numericId(data.cinemaId ?? existing.cinemaId)
        requireCinema(request, cinemaId)
        await one(db, 'cinema', cinemaId)
        if (type === 'showtime') {
          const hall = await one(db, 'hall', numericId(data.hallId ?? existing.hallId))
          assert(hall.cinemaId === cinemaId, 400, '影厅不属于该影院')
        }
      }
      if (type === 'hall' && (data.totalRows || data.totalCols)) data.totalSeats = Number(data.totalRows || existing.totalRows) * Number(data.totalCols || existing.totalCols)
      data.updateTime = now()
      await db.collection(spec.collection).doc(existing._id).update(data)
      return success(null, '更新成功')
    }
    if (method === 'DELETE') {
      requireUser(request, type === 'movie' || type === 'cinema' ? ['ADMIN'] : ['ADMIN', 'CINEMA_ADMIN'])
      const existing = await one(db, type, id)
      if (type === 'hall' || type === 'showtime') requireCinema(request, existing.cinemaId)
      await db.collection(spec.collection).doc(existing._id).remove()
      return success(null, '删除成功')
    }
  }
  if (method === 'PUT' && segments[2] === 'status' && segments.length === 3) {
    requireUser(request, type === 'movie' ? ['ADMIN'] : ['ADMIN', 'CINEMA_ADMIN'])
    const existing = await one(db, type, numericId(segments[1]))
    if (type !== 'movie') requireCinema(request, type === 'cinema' ? existing.id : existing.cinemaId)
    const status = Number(query.status)
    assert(Number.isInteger(status), 400, '状态无效')
    await db.collection(spec.collection).doc(existing._id).update({ status, updateTime: now() })
    return success(null, '状态更新成功')
  }
  throw new ApiError(404, '接口尚未迁移')
}

module.exports = { handle }
