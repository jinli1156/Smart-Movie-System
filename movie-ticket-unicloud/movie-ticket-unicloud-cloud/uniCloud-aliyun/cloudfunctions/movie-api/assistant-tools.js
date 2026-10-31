'use strict'

const { ApiError, assert, now, clean } = require('./core')
const catalog = require('./catalog')
const orders = require('./orders')
const seats = require('./seats')
const pricing = require('./pricing')
const recommend = require('./recommend')
const checkout = require('./checkout')
const watch = require('./assistant-plan')
const knowledge = require('./assistant-knowledge.json')

const definitions = require('./assistant-tools.json')
const enabled = new Set(['update_watch_plan', 'search_movies', 'search_cinemas', 'search_showtimes', 'get_seats', 'get_my_orders', 'recommend_movies', 'get_my_preferences', 'recommend_seats', 'compare_prices', 'quote_price', 'prepare_ticket_order', 'discover_movies', 'plan_movie_outing', 'search_knowledge'])
const toolDefinitions = definitions.filter(item => enabled.has(item.function.name))

function validArgs(name, args) {
  assert(args && typeof args === 'object' && !Array.isArray(args), 400, '工具参数无效')
  if (name === 'update_watch_plan') return args
  const schema = toolDefinitions.find(item => item.function.name === name)?.function.parameters
  assert(schema, 400, '工具不可用')
  assert(Object.keys(args).every(key => key in schema.properties), 400, '工具参数无效')
  for (const key of schema.required || []) assert(args[key] !== undefined, 400, '工具缺少必要参数')
  for (const [key, value] of Object.entries(args)) {
    const property = schema.properties[key]
    const types = Array.isArray(property.type) ? property.type : [property.type]
    assert(value === null ? types.includes('null') : types.some(type => type === 'integer' ? Number.isSafeInteger(value) : type === 'number' ? typeof value === 'number' && Number.isFinite(value) : type === 'boolean' ? typeof value === 'boolean' : type === 'string' ? typeof value === 'string' : false), 400, '工具参数类型无效')
    if (typeof value === 'number') assert((property.minimum === undefined || value >= property.minimum) && (property.maximum === undefined || value <= property.maximum), 400, '工具参数范围无效')
    if (typeof value === 'string') assert(value.length <= 200, 400, '工具参数过长')
    if (property.enum) assert(property.enum.includes(value), 400, '工具参数枚举无效')
  }
  return args
}

function queryRequest(path, query, user) { return { path, method: 'GET', query, user } }

async function searchCatalog(db, collectionName, args, keywordFields, fixedFilter = {}) {
  const filter = { deleted: 0, ...fixedFilter }
  if (args.status !== undefined) filter.status = args.status
  if (args.genre) filter.genre = new RegExp(String(args.genre).replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i')
  const keyword = String(args.keyword || '').trim()
  const escaped = keyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const condition = keyword
    ? db.command.and([filter, db.command.or(keywordFields.map(field => ({ [field]: new RegExp(escaped, 'i') })))])
    : filter
  const collection = db.collection(collectionName).where(condition)
  const page = args.page || 1, size = 8
  const [count, rows] = await Promise.all([
    collection.count(),
    collection.orderBy('id', 'asc').skip((page - 1) * size).limit(size).get()
  ])
  return { items: (rows.data || []).map(clean), total: count.total, page, size }
}

async function searchShowtimes(db, args, currentPlan) {
  const date = args.date || currentPlan.criteria.date || now().slice(0, 10)
  assert(/^\d{4}-\d{2}-\d{2}$/.test(date), 400, '日期无效')
  const filter = { status: 0, deleted: 0, startTime: db.command.gte(`${date}T00:00:00`).and(db.command.lt(`${date}T23:59:59.999`)) }
  if (args.movieId) filter.movieId = args.movieId
  if (args.cinemaId) filter.cinemaId = args.cinemaId
  const shows = (await db.collection('mts_showtime').where(filter).limit(1000).get()).data || []
  assert(shows.length < 1000, 503, '当日场次较多，请指定影片或影院')
  if (!shows.length) return { items: [], total: 0, page: args.page || 1, size: args.limit || 8 }
  const [movieResult, cinemaResult, hallResult, holdResult] = await Promise.all([
    db.collection('mts_movie').where({ id: db.command.in([...new Set(shows.map(item => item.movieId))]) }).limit(1000).get(),
    db.collection('mts_cinema').where({ id: db.command.in([...new Set(shows.map(item => item.cinemaId))]) }).limit(1000).get(),
    db.collection('mts_hall').where({ id: db.command.in([...new Set(shows.map(item => item.hallId))]) }).limit(1000).get(),
    db.collection('mts_seat_hold').where({ showtimeId: db.command.in(shows.map(item => item.id)) }).limit(1000).get()
  ])
  assert((holdResult.data || []).length < 1000, 503, '座位记录较多，无法安全估算余票')
  const movies = new Map((movieResult.data || []).map(item => [item.id, item]))
  const cinemas = new Map((cinemaResult.data || []).map(item => [item.id, item]))
  const halls = new Map((hallResult.data || []).map(item => [item.id, item]))
  const occupied = new Map()
  for (const hold of holdResult.data || []) if (hold.status === 'paid' || hold.status === 'pending' && hold.expiresAt > Date.now()) occupied.set(hold.showtimeId, (occupied.get(hold.showtimeId) || 0) + 1)
  const rows = shows.filter(show => {
    const movie = movies.get(show.movieId), cinema = cinemas.get(show.cinemaId), hall = halls.get(show.hallId)
    if (!movie || !cinema || !hall || movie.status !== 1 || cinema.status !== 1 || hall.status !== 1) return false
    if (Date.parse(show.startTime) <= Date.now() + 15 * 60000) return false
    if (args.genre && !String(movie.genre || '').includes(args.genre)) return false
    if (args.minRating && Number(movie.rating) < args.minRating) return false
    if (args.maxDuration && Number(movie.duration) > args.maxDuration) return false
    if (args.cinemaKeyword && !`${cinema.name} ${cinema.address}`.includes(args.cinemaKeyword)) return false
    if (args.hallKeyword && !String(hall.name).includes(args.hallKeyword)) return false
    if (args.startAfter && show.startTime.slice(11, 16) < args.startAfter) return false
    if (args.startBefore && show.startTime.slice(11, 16) >= args.startBefore) return false
    if (args.maxPrice !== undefined && (args.priceExclusive ? Number(show.basePrice) >= args.maxPrice : Number(show.basePrice) > args.maxPrice)) return false
    if (args.people && Number(show.totalSeats) - (occupied.get(show.id) || 0) < args.people) return false
    return true
  }).map(show => {
    const movie = movies.get(show.movieId), cinema = cinemas.get(show.cinemaId), hall = halls.get(show.hallId)
    return { id: show.id, movieId: movie.id, movieTitle: movie.title, moviePoster: movie.poster, movieGenre: movie.genre, movieRating: movie.rating, movieDuration: movie.duration, cinemaId: cinema.id, cinemaName: cinema.name, hallId: hall.id, hallName: hall.name, startTime: show.startTime, endTime: show.endTime, basePrice: show.basePrice, availableSeats: Math.max(0, Number(show.totalSeats) - (occupied.get(show.id) || 0)) }
  }).sort((a, b) => a.startTime.localeCompare(b.startTime) || a.id - b.id)
  const page = args.page || 1, size = args.limit || 8
  return { items: rows.slice((page - 1) * size, page * size), total: rows.length, page, size }
}

async function execute(name, raw, request, db, currentPlan) {
  let args
  try { args = validArgs(name, JSON.parse(raw || '{}')) }
  catch (error) { return { result: { ok: false, source: name, code: 'INVALID_ARGUMENTS', message: '工具参数无效，请重新查询。' }, plan: currentPlan } }
  if (name === 'update_watch_plan') {
    try { const plan = watch.patch(currentPlan, args); return { result: { ok: true, source: name, queriedAt: now(), data: plan }, plan } }
    catch (_) { return { result: { ok: false, source: name, code: 'INVALID_ARGUMENTS', message: '观影条件无效。' }, plan: currentPlan } }
  }
  if (['get_my_orders', 'get_my_preferences', 'quote_price', 'prepare_ticket_order'].includes(name) && !request.user) return { result: { ok: false, source: name, code: 'LOGIN_REQUIRED', message: '请先登录后查询本人数据或试算优惠。' }, plan: currentPlan }
  try {
    let data
    if (name === 'search_movies') data = await searchCatalog(db, 'mts_movie', args, ['title', 'director', 'actors'])
    else if (name === 'search_cinemas') data = await searchCatalog(db, 'mts_cinema', args, ['name', 'address'], { status: 1 })
    else if (name === 'search_showtimes') data = await searchShowtimes(db, args, currentPlan)
    else if (name === 'get_seats') {
      const result = (await orders.handle(queryRequest(`/showtime/${args.showtimeId}/seats`, {}, request.user), db)).data
      data = { ...result, showtimeId: args.showtimeId, bookable: result.availableSeats > 0 }
    } else if (name === 'get_my_orders') {
      if (args.orderNo) { const item = (await orders.handle(queryRequest(`/order/${encodeURIComponent(args.orderNo)}`, {}, request.user), db)).data; data = { items: [item], total: 1, page: 1, size: 1 } }
      else { const page = (await orders.handle(queryRequest('/order/my', { page: args.page || 1, size: 8, ...(args.status !== undefined ? { status: args.status } : {}) }, request.user), db)).data; data = { ...page, items: page.records } }
    }
    else if (name === 'recommend_movies') data = { movies: (await recommend.handle(queryRequest(request.user ? '/recommend/personal' : '/recommend/hot', { limit: args.limit || 5 }, request.user), db)).data }
    else if (name === 'get_my_preferences') data = (await recommend.handle(queryRequest('/recommend/profile', {}, request.user), db)).data
    else if (name === 'recommend_seats') {
      const value = (await seats.handle({ path: `/showtime/${args.showtimeId}/seat-recommendation`, method: 'POST', body: args, user: request.user }, db)).data
      data = { ...value, showtimeId: args.showtimeId }
    } else if (name === 'compare_prices') data = (await pricing.handle(queryRequest('/pricing/compare', args, request.user), db)).data
    else if (name === 'quote_price') {
      if (args.orderNo) data = (await pricing.handle(queryRequest(`/pricing/orders/${encodeURIComponent(args.orderNo)}/quote`, { usePoints: args.usePoints }, request.user), db)).data
      else {
        assert(args.showtimeId && args.people, 400, '请指定场次和人数')
        const show = (await db.collection('mts_showtime').where({ id: args.showtimeId }).limit(1).get()).data?.[0]
        assert(show, 404, '场次不存在')
        const discounts = await pricing.discounts(db, request.user.id)
        const originalPrice = Number(show.basePrice) * args.people
        const plans = pricing.plans(originalPrice, discounts.points, discounts.coupons, args.usePoints !== false)
        data = { showtimeId: args.showtimeId, originalPrice, plans: plans.slice(0, 3), bestPlan: plans[0], notice: '仅试算，支付时重新核验。' }
      }
    } else if (name === 'search_knowledge') {
      const query = String(args.query).toLowerCase()
      const score = doc => doc.keywords.split(' ').reduce((sum, word) => sum + (query.includes(word.toLowerCase()) ? word.length : 0), 0)
      data = knowledge.filter(doc => score(doc) > 0).sort((a, b) => score(b) - score(a)).slice(0, 3)
    } else if (name === 'prepare_ticket_order') {
      assert(currentPlan.criteria.showtimeId && currentPlan.criteria.people, 400, '请先确定场次和人数')
      data = (await checkout.handle({ method: 'POST', path: '/assistant/checkout/prepare', body: { criteria: currentPlan.criteria }, user: request.user }, db)).data
    } else if (name === 'discover_movies') {
      const rows = (await catalog.handle(queryRequest('/movie/now-showing', {}, request.user), db)).data
      const c = currentPlan.criteria
      const filtered = rows.filter(item => (!c.genre || String(item.genre || '').includes(c.genre)) && (!c.minRating || Number(item.rating) >= c.minRating) && (!c.maxDuration || Number(item.duration) <= c.maxDuration) && !(c.excludedMovieIds || []).includes(item.id) && !(c.excludedGenres || []).some(value => String(item.genre || '').includes(value)))
      data = { movies: filtered.slice(0, c.limit || 3), total: filtered.length, criteria: c }
    } else if (name === 'plan_movie_outing') {
      const c = currentPlan.criteria
      assert(c.people, 400, '请先说明观影人数')
      const found = await searchShowtimes(db, { ...c, limit: 1000, page: 1 }, currentPlan)
      const account = await pricing.discounts(db, request.user?.id)
      const eligible = []
      for (const item of found.items) {
        if (c.showtimeId && item.id !== c.showtimeId) continue
        if ((c.excludedMovieIds || []).includes(item.movieId) || (c.excludedGenres || []).some(value => String(item.movieGenre || '').includes(value))) continue
        if (c.endBefore && item.endTime.slice(11, 16) > c.endBefore) continue
        const originalPrice = Math.round(Number(item.basePrice) * c.people * 100) / 100
        const bestPlan = pricing.plans(originalPrice, account.points, c.useCoupons === false ? [] : account.coupons, c.usePoints !== false)[0]
        if (c.maxTotal !== undefined && bestPlan.totalPrice > c.maxTotal) continue
        eligible.push({ item, originalPrice, bestPlan })
      }
      eligible.sort((a, b) => a.bestPlan.totalPrice - b.bestPlan.totalPrice || a.item.startTime.localeCompare(b.item.startTime))
      const plans = []
      for (const { item, originalPrice, bestPlan } of eligible) {
        if (plans.length >= (c.limit || 3)) break
        const hall = (await db.collection('mts_hall').where({ id: item.hallId }).limit(1).get()).data?.[0]
        const holds = (await db.collection('mts_seat_hold').where({ showtimeId: item.id }).limit(1000).get()).data || []
        assert(holds.length < 1000, 503, '座位记录较多，请到选座页面确认')
        const occupied = new Set(holds.filter(value => value.status === 'paid' || value.status === 'pending' && value.expiresAt > Date.now()).map(value => value.seat))
        const picked = hall ? seats.recommend(hall, item, occupied, c.people, seats.infer(c.naturalLanguage, c.preference), 1)[0] : null
        if (c.requireTogether !== false && !picked) continue
        plans.push({ ...item, showtimeId: item.id, people: c.people, originalPrice, bestPlan, seats: picked?.seats || [], seatReason: picked?.reason || '', reason: '满足当前筛选条件，按预计实付排序' })
      }
      data = { plans, total: found.total, people: c.people, criteria: c, usePoints: c.usePoints !== false, useCoupons: c.useCoupons !== false, notice: '方案仅供参考，座位与优惠在下单和支付时重新校验。' }
    } else throw new ApiError(400, '工具不可用')
    return { result: { ok: true, source: name, queriedAt: now(), data }, plan: currentPlan }
  } catch (error) {
    return { result: { ok: false, source: name, code: error.code === 401 ? 'LOGIN_REQUIRED' : error instanceof ApiError ? 'BUSINESS_RULE' : 'QUERY_FAILED', message: error instanceof ApiError ? error.message : '查询暂时失败，请稍后重试。' }, plan: currentPlan }
  }
}

module.exports = { definitions: toolDefinitions, execute }
