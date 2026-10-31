'use strict'

const { ApiError, assert, success, numericId, now, clean, nextId, requireUser } = require('./core')

function limit(value, fallback) {
  const count = Number(value || fallback)
  assert(Number.isSafeInteger(count) && count >= 1 && count <= 30, 400, '数量无效')
  return count
}

function genres(movie) { return String(movie.genre || '').split(/[,，、/]/).map(item => item.trim()).filter(Boolean) }

async function movies(db) {
  const found = await db.collection('mts_movie').where({ status: 1, deleted: 0 }).limit(1000).get()
  return (found.data || []).map(clean)
}

async function behaviors(db, userId) {
  const found = await db.collection('mts_behavior').where({ userId }).limit(1000).get()
  return found.data || []
}

async function profile(db, userId, allMovies, signals) {
  const byId = new Map(allMovies.map(movie => [movie.id, movie]))
  const score = new Map()
  for (const behavior of signals.filter(item => item.active !== 0)) {
    const movie = byId.get(behavior.movieId)
    if (!movie) continue
    for (const genre of genres(movie)) score.set(genre, (score.get(genre) || 0) + (behavior.behaviorType === 'FAVORITE' ? 3 : Math.min(3, behavior.behaviorCount || 1)))
  }
  const favoriteGenres = [...score].sort((a, b) => b[1] - a[1]).map(item => item[0]).slice(0, 5)
  const orders = await db.collection('mts_order').where({ userId, status: 1 }).limit(1000).get()
  const comments = await db.collection('mts_comment').where({ userId }).limit(1000).get()
  const viewedCount = signals.filter(item => item.behaviorType === 'VIEW').length
  const favoriteCount = signals.filter(item => item.behaviorType === 'FAVORITE' && item.active !== 0).length
  const purchasedCount = orders.data?.length || 0
  const ratedCount = comments.data?.length || 0
  return { needsColdStart: viewedCount + favoriteCount + purchasedCount + ratedCount < 3, favoriteGenres, viewedCount, favoriteCount, purchasedCount, ratedCount, summary: favoriteGenres.length ? `偏好${favoriteGenres.join('、')}类型影片` : '暂无足够的观影偏好数据', analysisSource: 'algorithm', model: null }
}

function rank(allMovies, signals, preferred) {
  const weights = new Map()
  for (const behavior of signals.filter(item => item.active !== 0)) weights.set(behavior.movieId, (weights.get(behavior.movieId) || 0) + (behavior.behaviorType === 'FAVORITE' ? 3 : 1))
  const reference = new Map(allMovies.map(movie => [movie.id, movie]))
  const genreWeights = new Map()
  for (const [id, weight] of weights) for (const genre of genres(reference.get(id) || {})) genreWeights.set(genre, (genreWeights.get(genre) || 0) + weight)
  for (const genre of preferred) genreWeights.set(genre, (genreWeights.get(genre) || 0) + 3)
  return allMovies.map(movie => {
    const preference = Math.min(100, genres(movie).reduce((sum, genre) => sum + (genreWeights.get(genre) || 0) * 12, 0))
    const trend = Math.min(100, Number(movie.rating || 0) * 10)
    const behavior = Math.min(100, (weights.get(movie.id) || 0) * 20)
    const matchScore = Math.round(preference * 0.45 + trend * 0.4 + behavior * 0.15)
    return { ...movie, statusText: '正在热映', matchScore, recommendationReason: preference > 0 ? '符合你的观影类型偏好' : '根据影片口碑推荐', recommendationScores: { content: preference, userPreference: preference, behavior, scene: 0, trend }, analysisSource: 'algorithm', model: null }
  }).sort((a, b) => b.matchScore - a.matchScore || a.id - b.id)
}

async function evaluation(db, k) {
  const catalog = await movies(db)
  const users = (await db.collection('mts_user').where({ deleted: 0, role: 'USER' }).limit(1000).get()).data || []
  const samples = []
  for (const user of users) {
    const signals = await behaviors(db, user.id)
    const positives = signals.filter(item => item.behaviorType === 'FAVORITE' && item.active !== 0 && catalog.some(movie => movie.id === item.movieId)).sort((a, b) => String(a.updateTime || '').localeCompare(String(b.updateTime || '')))
    if (positives.length < 2) continue
    samples.push({ user, training: signals.filter(item => item.movieId !== positives.at(-1).movieId), target: positives.at(-1).movieId })
  }
  const run = (key, name, category, mode) => {
    let hits = 0, ndcg = 0
    const seen = new Set()
    let diversity = 0
    for (const sample of samples) {
      const candidates = mode === 'popular' ? catalog.slice().sort((a, b) => Number(b.rating || 0) - Number(a.rating || 0))
        : rank(catalog, mode === 'no_behavior' ? [] : sample.training, mode === 'no_preference' ? [] : sample.user.favoriteGenres || [])
      const picks = candidates.filter(item => !sample.training.some(signal => signal.movieId === item.id && signal.behaviorType === 'FAVORITE' && signal.active !== 0)).slice(0, k)
      picks.forEach(item => seen.add(item.id))
      diversity += new Set(picks.flatMap(genres)).size / Math.max(1, picks.length)
      const position = picks.findIndex(item => item.id === sample.target)
      if (position >= 0) { hits++; ndcg += 1 / Math.log2(position + 2) }
    }
    const evaluatedUsers = samples.length
    return { key, name, category, evaluatedUsers, precisionAtK: evaluatedUsers ? hits / (evaluatedUsers * k) : 0, recallAtK: evaluatedUsers ? hits / evaluatedUsers : 0, ndcgAtK: evaluatedUsers ? ndcg / evaluatedUsers : 0, catalogCoverage: catalog.length ? seen.size / catalog.length : 0, diversity: evaluatedUsers ? diversity / evaluatedUsers : 0, recallChangeVsFull: 0, description: '按影片留出，使用当前汇总数据；非严格历史回放' }
  }
  const full = run('full', '当前规则推荐', 'FULL', 'full')
  const baselines = [run('popular', '评分排序', 'BASELINE', 'popular')]
  const ablations = [run('no_behavior', '移除行为信号', 'ABLATION', 'no_behavior'), run('no_preference', '移除显式偏好', 'ABLATION', 'no_preference')]
  for (const item of [...baselines, ...ablations]) item.recallChangeVsFull = item.recallAtK - full.recallAtK
  return { k, evaluatedUsers: full.evaluatedUsers, precisionAtK: full.precisionAtK, recallAtK: full.recallAtK, ndcgAtK: full.ndcgAtK, catalogCoverage: full.catalogCoverage, diversity: full.diversity, skippedUsers: users.length - samples.length, baselines, ablations, protocol: '云端规则诊断：每位用户留出最后一部想看影片，移除该片行为后使用当前汇总行为、评分与偏好排序。未恢复历史数据快照，可能含后续信息，不能作为严格时序泛化实验，也不能与 Spring Boot 混合推荐实验直接比较。样本不足的用户跳过。', candidatePolicy: '当前正在热映且未在训练集中标记想看的影片；多样性为平均每条推荐的不同类型数/影片数，可大于 1。', generatedAt: now() }
}

async function upsertBehavior(db, userId, movieId, behaviorType, active) {
  const collection = db.collection('mts_behavior')
  const found = await collection.where({ userId, movieId, behaviorType }).limit(1).get()
  const current = found.data?.[0]
  if (current) {
    await collection.doc(current._id).update({ active, behaviorCount: (current.behaviorCount || 0) + 1, updateTime: now() })
  } else {
    try { await collection.add({ id: await nextId(db, 'behavior'), userId, movieId, behaviorType, active, behaviorCount: 1, createTime: now(), updateTime: now() }) }
    catch (error) {
      const retry = await collection.where({ userId, movieId, behaviorType }).limit(1).get()
      if (!retry.data?.length) throw error
      await collection.doc(retry.data[0]._id).update({ active, updateTime: now() })
    }
  }
}

async function handle(request, db) {
  const { method, path, query, body } = request
  if (path === '/recommend/evaluation') requireUser(request, ['ADMIN'])
  const count = limit(query.limit, path.includes('similar') ? 6 : 10)
  if (method === 'GET' && path === '/recommend/hot') return success((await movies(db)).sort((a, b) => Number(b.rating || 0) - Number(a.rating || 0)).slice(0, count))
  const similar = /^\/recommend\/similar\/(\d+)$/.exec(path)
  if (method === 'GET' && similar) {
    const catalog = await movies(db)
    const source = catalog.find(item => item.id === numericId(similar[1]))
    if (!source) return success([])
    const sourceGenres = new Set(genres(source))
    return success(catalog.filter(item => item.id !== source.id).sort((a, b) => genres(b).filter(item => sourceGenres.has(item)).length - genres(a).filter(item => sourceGenres.has(item)).length || Number(b.rating || 0) - Number(a.rating || 0)).slice(0, count))
  }
  const userId = requireUser(request).id
  const behaviorMatch = /^\/recommend\/behavior\/(view|favorite)\/(\d+)$/.exec(path)
  if (behaviorMatch) {
    const movieId = numericId(behaviorMatch[2])
    const kind = behaviorMatch[1] === 'view' ? 'VIEW' : 'FAVORITE'
    if (method === 'POST' && kind === 'VIEW' || method === 'PUT' && kind === 'FAVORITE') {
      const found = await db.collection('mts_movie').where({ id: movieId }).limit(1).get()
      assert(found.data?.length, 404, '影片不存在')
      const existing = (await db.collection('mts_behavior').where({ userId, movieId, behaviorType: kind }).limit(1).get()).data?.[0]
      const active = kind === 'VIEW' || existing?.active === 0 ? 1 : 0
      await upsertBehavior(db, userId, movieId, kind, active)
      return success(kind === 'FAVORITE' ? active === 1 : null)
    }
    if (method === 'DELETE' && kind === 'FAVORITE') { await upsertBehavior(db, userId, movieId, kind, 0); return success() }
    if (method === 'GET' && kind === 'FAVORITE') return success((await behaviors(db, userId)).some(item => item.movieId === movieId && item.behaviorType === 'FAVORITE' && item.active !== 0))
  }
  const signals = await behaviors(db, userId)
  const catalog = await movies(db)
  if (method === 'GET' && path === '/recommend/favorites') {
    const ids = new Map(signals.filter(item => item.behaviorType === 'FAVORITE' && item.active !== 0).map(item => [item.movieId, item.updateTime]))
    return success(catalog.filter(item => ids.has(item.id)).map(item => ({ ...item, favoriteTime: ids.get(item.id) })))
  }
  if (method === 'POST' && path === '/recommend/cold-start') {
    assert(Array.isArray(body?.movieIds) && body.movieIds.length >= 3 && body.movieIds.length <= 10 && new Set(body.movieIds).size === body.movieIds.length, 400, '请选择3至10部不同的影片')
    assert(!body.favoriteGenres || (Array.isArray(body.favoriteGenres) && body.favoriteGenres.length <= 5 && body.favoriteGenres.every(item => typeof item === 'string')), 400, '影片类型无效')
    for (const id of body.movieIds) {
      assert(catalog.some(item => item.id === numericId(id)), 400, '包含不存在的影片')
      await upsertBehavior(db, userId, id, 'FAVORITE', 1)
    }
    await db.collection('mts_user').where({ id: userId }).update({ favoriteGenres: body.favoriteGenres || [], updateTime: now() })
    return success(null, '偏好初始化成功')
  }
  const user = (await db.collection('mts_user').where({ id: userId }).limit(1).get()).data?.[0]
  const facts = await profile(db, userId, catalog, signals)
  if (method === 'GET' && path === '/recommend/profile') return success(facts)
  if (method === 'GET' && path === '/recommend/profile/radar') {
    const rated = Math.min(100, facts.ratedCount * 20)
    const viewed = Math.min(100, facts.viewedCount * 10)
    const favorite = Math.min(100, facts.favoriteCount * 20)
    const evidenceCount = facts.viewedCount + facts.favoriteCount + facts.purchasedCount + facts.ratedCount
    return success({ dimensions: { 内容契合: favorite, 显式偏好: Math.min(100, (user?.favoriteGenres || []).length * 20), 行为相似: viewed, 场景适配: 0, 近期趋势: rated }, topGenres: facts.favoriteGenres, evidenceCount, summary: facts.summary })
  }
  if (method === 'GET' && path === '/recommend/personal') return success(rank(catalog, signals, user?.favoriteGenres || []).slice(0, count))
  if (method === 'GET' && path === '/recommend/evaluation') {
    requireUser(request, ['ADMIN'])
    const k = Number(query.k || 6)
    assert(Number.isSafeInteger(k) && k >= 1 && k <= 20, 400, '评估数量无效')
    return success(await evaluation(db, k))
  }
  throw new ApiError(404, '接口不存在')
}

module.exports = { handle, genres, rank }
