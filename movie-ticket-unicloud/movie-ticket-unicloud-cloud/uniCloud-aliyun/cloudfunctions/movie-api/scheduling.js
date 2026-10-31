'use strict'

const crypto = require('crypto')
const { ApiError, assert, success, numericId, now, nextId, requireUser } = require('./core')
const { scopeCinemaId, requireCinema } = require('./permissions')

function dateToday() { return new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Shanghai' }) }
function dateValid(value) {
  const day = Date.parse(`${value}T00:00:00Z`)
  const today = Date.parse(`${dateToday()}T00:00:00Z`)
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(day) && day >= today && day <= today + 30 * 86400000
}

async function own(db, id, userId) {
  const found = await db.collection('mts_scheduling_draft').where({ id, userId }).limit(1).get()
  assert(found.data?.length, 404, '暂存草案不存在或已更新')
  return found.data[0]
}

async function summary(db, draft) {
  const found = await db.collection('mts_cinema').where({ id: draft.cinemaId }).limit(1).get()
  return { id: draft.id, cinemaId: draft.cinemaId, cinemaName: found.data?.[0]?.name || '', targetDate: draft.targetDate, savedAt: draft.savedAt }
}

function millis(value) { return Date.parse(/[Zz]|[+-]\d\d:\d\d$/.test(String(value)) ? value : `${String(value).replace(' ', 'T')}+08:00`) }
function same(a, b) { return a.movieId === b.movieId && a.hallId === b.hallId && a.startTime === b.startTime && a.endTime === b.endTime && Number(a.basePrice) === Number(b.basePrice) }
function digest(rows, holds) {
  const payload = rows.slice().sort((a, b) => a.id - b.id).map(item => [item.id, item.movieId, item.hallId, item.startTime, item.endTime, item.basePrice, item.status, item.bookingVersion || 0])
  const occupied = holds.filter(item => item.status === 'paid' || item.status === 'pending' && item.expiresAt > Date.now()).map(item => [item.showtimeId, item.seat, item.status]).sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b)))
  return crypto.createHash('sha256').update(JSON.stringify([payload, occupied])).digest('hex')
}

async function state(db, cinemaId, targetDate, command = db.command) {
  assert(dateValid(targetDate), 400, '请选择今天起30天内的日期')
  const cinema = (await db.collection('mts_cinema').where({ id: cinemaId, status: 1 }).limit(1).get()).data?.[0]
  assert(cinema, 404, '影院不可用')
  const [showRows, hallResult, movieResult] = await Promise.all([
    (async () => {
      const rows = []
      for (let page = 0; page < 20; page++) {
        const result = await db.collection('mts_showtime').where({ cinemaId }).skip(page * 1000).limit(1000).get()
        rows.push(...(result.data || []))
        if ((result.data || []).length < 1000) return rows
      }
      throw new ApiError(503, '影院场次过多，请联系管理员归档历史数据')
    })(),
    db.collection('mts_hall').where({ cinemaId }).limit(1000).get(),
    db.collection('mts_movie').limit(1000).get()
  ])
  const nearby = showRows.filter(item => item.status !== 2 && millis(item.startTime) < Date.parse(`${targetDate}T00:00:00Z`) + 48 * 3600000 && millis(item.endTime) > Date.parse(`${targetDate}T00:00:00Z`) - 3600000)
  const current = nearby.filter(item => item.startTime?.slice(0, 10) === targetDate)
  const halls = hallResult.data || []
  const movies = movieResult.data || []
  const holds = current.length ? (await db.collection('mts_seat_hold').where({ showtimeId: command.in(current.map(item => item.id)) }).limit(1000).get()).data || [] : []
  assert(holds.length < 1000, 503, '座位记录过多，暂不能安全应用排片')
  const active = holds.filter(item => item.status === 'paid' || item.status === 'pending' && item.expiresAt > Date.now())
  const protectedIds = new Set(current.filter(item => millis(item.startTime) <= Date.now() || active.some(hold => hold.showtimeId === item.id)).map(item => item.id))
  return { nearby, current, halls, movies, holds, protectedIds, version: digest(nearby, holds) }
}

function entry(show, state) {
  return { sourceShowtimeId: show.id, movieId: show.movieId, movieTitle: state.movies.find(item => item.id === show.movieId)?.title || '', hallId: show.hallId, hallName: state.halls.find(item => item.id === show.hallId)?.name || '', startTime: show.startTime, endTime: show.endTime, basePrice: show.basePrice, locked: state.protectedIds.has(show.id), lockReason: millis(show.startTime) <= Date.now() ? '已开场' : state.protectedIds.has(show.id) ? '已售或待付款' : '' }
}

function evaluate(items, state) {
  let expectedRevenue = 0, expectedAudience = 0, capacity = 0
  for (const item of items) {
    const hall = state.halls.find(h => h.id === item.hallId)
    const seats = Number(hall?.totalSeats || 0)
    capacity += seats
    expectedAudience += Math.round(seats * 0.3)
    expectedRevenue += Math.round(seats * 0.3) * Number(item.basePrice || 0)
  }
  return { showtimeCount: items.length, distinctMovies: new Set(items.map(item => item.movieId)).size, expectedRevenue: Math.round(expectedRevenue * 100) / 100, expectedAudience, occupancyRate: capacity ? Math.round(expectedAudience * 1000 / capacity) / 10 : null, historicalSamples: 0, items: items.map(item => ({ ...entry(item, state), predictedRate: 30, expectedAudience: Math.round(Number(state.halls.find(h => h.id === item.hallId)?.totalSeats || 0) * 0.3), expectedRevenue: Math.round(Number(state.halls.find(h => h.id === item.hallId)?.totalSeats || 0) * 0.3 * Number(item.basePrice || 0) * 100) / 100, confidence: 'LOW' })) }
}

function validateDraft(body, state) {
  assert(body?.version === state.version, 409, '排片或售票状态已变化，请刷新工作台')
  assert(Array.isArray(body.items) && body.items.length > 0 && body.items.length <= 300, 400, '草案至少保留一场且不能超过300场')
  const cinemaId = numericId(body.cinemaId)
  const targetDate = String(body.targetDate)
  const cleaningMinutes = Math.max(15, Number(body.cleaningMinutes) || 15)
  assert(Number.isSafeInteger(cleaningMinutes) && cleaningMinutes <= 180, 400, '清场时间无效')
  const currentMap = new Map(state.current.map(item => [item.id, item]))
  const used = new Set()
  const proposed = []
  for (const raw of body.items) {
    const id = raw.sourceShowtimeId ? numericId(raw.sourceShowtimeId) : null
    assert(!id || currentMap.has(id) && !used.has(id), 400, '草案包含重复或其他日期的场次')
    if (id) used.add(id)
    const item = { id, movieId: numericId(raw.movieId), hallId: numericId(raw.hallId), cinemaId, startTime: String(raw.startTime), endTime: String(raw.endTime), basePrice: Number(raw.basePrice) }
    const old = id ? currentMap.get(id) : null
    if (old && same(old, item)) { proposed.push(old); continue }
    assert(!id || !state.protectedIds.has(id), 409, '已售、待付款或已开场的场次不可修改')
    const movie = state.movies.find(value => value.id === item.movieId && value.status === 1)
    const hall = state.halls.find(value => value.id === item.hallId && value.status === 1)
    assert(movie && hall, 400, '影片或影厅不可用')
    assert(item.startTime.slice(0, 10) === targetDate && millis(item.startTime) > Date.now(), 400, '开场时间必须在目标日期的未来')
    assert(millis(item.endTime) - millis(item.startTime) === Number(movie.duration || 120) * 60000, 400, '结束时间必须与影片时长一致')
    assert(Number.isFinite(item.basePrice) && item.basePrice >= 0 && item.basePrice <= 100000, 400, '票价无效')
    item.totalSeats = hall.totalSeats
    item.availableSeats = hall.totalSeats
    proposed.push(item)
  }
  assert([...state.protectedIds].every(id => used.has(id)), 409, '必须保留全部已售、待付款或已开场的场次')
  const combined = [...proposed, ...state.nearby.filter(item => item.startTime?.slice(0, 10) !== targetDate)].sort((a, b) => a.hallId - b.hallId || millis(a.startTime) - millis(b.startTime))
  for (let index = 1; index < combined.length; index++) {
    const before = combined[index - 1], after = combined[index]
    if (before.hallId === after.hallId && millis(after.startTime) < millis(before.endTime) + cleaningMinutes * 60000) {
      const prior = state.nearby.find(item => item.id === before.id && same(item, before)) && state.nearby.find(item => item.id === after.id && same(item, after))
      assert(prior, 409, '影厅场次冲突，需预留清场时间')
    }
  }
  const changes = []
  for (const item of proposed) {
    const old = currentMap.get(item.id)
    if (!old) changes.push({ type: 'ADD', after: entry(item, state) })
    else if (!same(old, item)) changes.push({ type: 'UPDATE', before: entry(old, state), after: entry(item, state) })
  }
  for (const item of state.current) if (!used.has(item.id)) changes.push({ type: 'REMOVE', before: entry(item, state) })
  return { proposed, changes, cleaningMinutes }
}

async function handle(request, db) {
  const { method, path, body } = request
  const userId = requireUser(request, ['ADMIN', 'CINEMA_ADMIN']).id
  const scope = scopeCinemaId(request, null)
  if (method === 'GET' && path === '/statistics/scheduling/workspace') {
    requireCinema(request, numericId(request.query.cinemaId))
    const stateValue = await state(db, numericId(request.query.cinemaId), String(request.query.targetDate || ''))
    return success({ version: stateValue.version, minimumCleaningMinutes: 15, items: stateValue.current.map(item => entry(item, stateValue)), halls: stateValue.halls.map(({ _id, ...hall }) => hall) })
  }
  if (method === 'POST' && ['/statistics/scheduling/check', '/statistics/scheduling/apply'].includes(path)) {
    const cinemaId = numericId(body?.cinemaId)
    requireCinema(request, cinemaId)
    const targetDate = String(body?.targetDate || '')
    if (path.endsWith('/check')) {
      const current = await state(db, cinemaId, targetDate)
      const valid = validateDraft(body, current)
      return success({ valid: true, cleaningMinutes: valid.cleaningMinutes, changes: valid.changes, original: evaluate(current.current, current), draft: evaluate(valid.proposed, current) })
    }
    const tx = await db.startTransaction()
    try {
      const current = await state(tx, cinemaId, targetDate, db.command)
      const valid = validateDraft(body, current)
      const kept = new Set(valid.proposed.filter(item => current.current.some(old => old.id === item.id && same(old, item))).map(item => item.id))
      let removed = 0, created = 0
      for (const old of current.current) if (!kept.has(old.id)) {
        await tx.collection('mts_showtime').doc(old._id).remove()
        removed++
      }
      for (const item of valid.proposed) if (!kept.has(item.id)) {
        const { id, ...fields } = item
        await tx.collection('mts_showtime').add({ ...fields, id: await nextId(db, 'showtime'), status: 0, createTime: now(), updateTime: now() })
        created++
      }
      await tx.commit()
      return success({ created, removed, message: '排片已应用' })
    } catch (error) { await tx.rollback().catch(() => {}); throw error }
  }
  if (path === '/statistics/scheduling/saved-drafts') {
    if (method === 'GET') {
      const found = await db.collection('mts_scheduling_draft').where({ userId, ...(scope == null ? {} : { cinemaId: scope }) }).orderBy('savedAt', 'desc').limit(100).get()
      return success(await Promise.all((found.data || []).map(item => summary(db, item))))
    }
    if (method === 'PUT') {
      const cinemaId = numericId(body?.cinemaId)
      requireCinema(request, cinemaId)
      const targetDate = String(body?.targetDate || '')
      assert(dateValid(targetDate), 400, '只能暂存今天起30天内的草案')
      const content = body?.content
      assert(content && typeof content === 'object' && !Array.isArray(content) && Array.isArray(content.draft) && content.draft.length <= 300 && content.workspace && typeof content.workspace.version === 'string' && Array.isArray(content.workspace.items) && content.settings && Number.isInteger(content.draftCleaning) && JSON.stringify(content).length <= 512000, 400, '草案格式无效或内容过大')
      const cinema = (await db.collection('mts_cinema').where({ id: cinemaId, status: 1 }).limit(1).get()).data?.[0]
      assert(cinema, 400, '影院不可用')
      const collection = db.collection('mts_scheduling_draft')
      const previous = (await collection.where({ userId, cinemaId, targetDate }).limit(1).get()).data?.[0]
      if (previous) await collection.doc(previous._id).remove()
      const draft = { id: crypto.randomUUID(), userId, cinemaId, targetDate, content, savedAt: now() }
      await collection.add(draft)
      return success({ summary: await summary(db, draft), content })
    }
  }
  const match = /^\/statistics\/scheduling\/saved-drafts\/([0-9a-fA-F-]{36})$/.exec(path)
  if (match) {
    const draft = await own(db, match[1], userId)
    requireCinema(request, draft.cinemaId)
    if (method === 'GET') return success({ summary: await summary(db, draft), content: draft.content })
    if (method === 'DELETE') { await db.collection('mts_scheduling_draft').doc(draft._id).remove(); return success() }
  }
  throw new ApiError(501, '智能排片接口尚未迁移')
}

module.exports = { handle }
