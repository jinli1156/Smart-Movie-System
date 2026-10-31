'use strict'

const { ApiError, assert, success, numericId, now, requireUser } = require('./core')
const { scopeCinemaId, requireCinema } = require('./permissions')

function millis(value) { return Date.parse(/[Zz]|[+-]\d\d:\d\d$/.test(String(value)) ? value : `${String(value).replace(' ', 'T')}+08:00`) }
function local(ms) { return new Date(ms + 8 * 3600000).toISOString().slice(0, 19) + '+08:00' }
function clamp(value) { return Math.max(5, Math.min(95, Math.round(value * 10) / 10)) }
function weekday(value) { return (new Date(millis(value) + 8 * 3600000).getUTCDay() + 6) % 7 + 1 }
function slot(value) { const hour = Number(String(value).slice(11, 13)); return hour >= 6 && hour < 12 ? 0 : hour >= 12 && hour < 18 ? 1 : hour >= 18 && hour < 22 ? 2 : 3 }
function confidence(count) { return count >= 20 ? '高' : count >= 5 ? '中' : '低' }

async function all(db, name, filter = {}) {
  const result = []
  for (let page = 0; page < 20; page++) {
    let query = db.collection(name)
    if (Object.keys(filter).length) query = query.where(filter)
    const found = await query.skip(page * 1000).limit(1000).get()
    result.push(...(found.data || []))
    if ((found.data || []).length < 1000) return result
  }
  throw new ApiError(503, '历史数据过多，请缩小分析范围')
}

async function basis(db, cinemaId) {
  const cinema = (await db.collection('mts_cinema').where({ id: cinemaId, deleted: 0 }).limit(1).get()).data?.[0]
  assert(cinema, 404, '影院不存在')
  const [halls, movies, shows, orders] = await Promise.all([
    all(db, 'mts_hall', { cinemaId, deleted: 0 }), all(db, 'mts_movie', { deleted: 0 }),
    all(db, 'mts_showtime', { cinemaId, deleted: 0 }), all(db, 'mts_order')
  ])
  const paidByShow = new Map()
  for (const order of orders) if (order.status === 1) paidByShow.set(order.showtimeId, (paidByShow.get(order.showtimeId) || 0) + Number(order.seatCount || 0))
  const samples = shows.filter(item => millis(item.startTime) < Date.now() && item.status !== 2 && Number(item.totalSeats) > 0).map(item => ({ ...item, sold: paidByShow.get(item.id) || 0, rate: Math.min(100, (paidByShow.get(item.id) || 0) * 100 / Number(item.totalSeats)) }))
  return { cinema, halls, movies, shows, orders, samples }
}

function predict(data, movieId, hallId, startTime) {
  const movie = data.movies.find(item => item.id === movieId)
  const hall = data.halls.find(item => item.id === hallId)
  assert(movie && hall, 400, '影片或影厅不可用')
  const sameMovie = data.samples.filter(item => item.movieId === movieId)
  const sameHall = data.samples.filter(item => item.hallId === hallId)
  const sameSlot = data.samples.filter(item => slot(item.startTime) === slot(startTime))
  const sameDay = data.samples.filter(item => weekday(item.startTime) === weekday(startTime))
  const mean = values => values.length ? values.reduce((sum, item) => sum + item.rate, 0) / values.length : 30
  const historical = mean(data.samples)
  const movieRate = mean(sameMovie)
  const hallRate = mean(sameHall)
  const slotRate = mean(sameSlot)
  const weekdayRate = mean(sameDay)
  const ratingPrior = Number(movie.rating || 6) * 5
  const raw = historical * 0.2 + movieRate * 0.35 + hallRate * 0.1 + slotRate * 0.15 + weekdayRate * 0.1 + ratingPrior * 0.1
  const shrink = Math.min(1, data.samples.length / 30)
  const predictedRate = clamp(30 * (1 - shrink) + raw * shrink)
  return { movieId, cinemaId: data.cinema.id, hallId, startTime, predictedRate, confidence: confidence(sameMovie.length), historicalSamples: sameMovie.length, expectedAudience: Math.round(Number(hall.totalSeats || 0) * predictedRate / 100), drivers: [
    { name: '影片历史', impact: Math.round((movieRate - historical) * 0.35 * 10) / 10, direction: movieRate >= historical ? 'UP' : 'DOWN' },
    { name: '时段', impact: Math.round((slotRate - historical) * 0.15 * 10) / 10, direction: slotRate >= historical ? 'UP' : 'DOWN' },
    { name: '星期', impact: Math.round((weekdayRate - historical) * 0.1 * 10) / 10, direction: weekdayRate >= historical ? 'UP' : 'DOWN' }
  ] }
}

function analysis(summary, samples) {
  return { source: 'algorithm', status: 'success', generatedAt: now(), summary, dataBasis: { historicalSamples: samples, coldStart: samples < 5, occupancyBasis: '已支付订单售票数/影厅座位数', predictionBasis: '影片、影厅、时段、星期和口碑的加权历史均值' }, actions: [], risks: samples < 5 ? ['历史样本较少，预测仅供参考'] : [] }
}

function metrics(data, rows, method, elapsed) {
  const audience = rows.reduce((sum, item) => sum + Number(item.expectedAudience || 0), 0)
  const capacity = rows.reduce((sum, item) => sum + Number(data.halls.find(hall => hall.id === item.hallId)?.totalSeats || 0), 0)
  return { method, showtimeCount: rows.length, expectedRevenue: Math.round(rows.reduce((sum, item) => sum + Number(item.expectedRevenue || 0), 0) * 100) / 100, expectedAudience: audience, occupancyRate: capacity ? Math.round(audience * 1000 / capacity) / 10 : 0, distinctMovies: new Set(rows.map(item => item.movieId)).size, calculationTimeMs: elapsed, constraintSatisfactionRate: 100 }
}

async function insights(db, cinemaId) {
  const data = await basis(db, cinemaId)
  const heatmap = []
  for (const hall of data.halls) for (let day = 1; day <= 7; day++) for (let timeSlot = 0; timeSlot < 4; timeSlot++) {
    const subset = data.samples.filter(item => item.hallId === hall.id && weekday(item.startTime) === day && slot(item.startTime) === timeSlot)
    heatmap.push({ hallId: hall.id, weekday: day, weekdayLabel: `周${'一二三四五六日'[day - 1]}`, timeSlot, timeSlotLabel: ['上午', '下午', '黄金', '深夜'][timeSlot], occupancyRate: subset.length ? Math.round(subset.reduce((sum, item) => sum + item.rate, 0) / subset.length * 10) / 10 : null, sampleCount: subset.length })
  }
  const future = data.shows.filter(item => item.status === 0 && millis(item.startTime) > Date.now() && millis(item.startTime) < Date.now() + 14 * 86400000 && data.movies.some(movie => movie.id === item.movieId) && data.halls.some(hall => hall.id === item.hallId))
  const referenceHall = data.halls.find(hall => hall.status === 1) || data.halls[0]
  const rankedMovies = data.movies.filter(movie => movie.status === 1 && referenceHall).map(movie => {
    const rate = predict(data, movie.id, referenceHall.id, local(Date.now() + 86400000)).predictedRate
    return { movieId: movie.id, movieTitle: movie.title, predictedRate: rate, confidence: confidence(data.samples.filter(item => item.movieId === movie.id).length), reason: '按历史上座率与影片口碑估算' }
  }).sort((a, b) => b.predictedRate - a.predictedRate)
  const lowOccupancyShowtimes = future.map(item => {
    const prediction = predict(data, item.movieId, item.hallId, item.startTime)
    const replacement = rankedMovies.find(movie => movie.movieId !== item.movieId && movie.predictedRate > prediction.predictedRate + 5)
    return { showtimeId: item.id, movieTitle: data.movies.find(movie => movie.id === item.movieId)?.title || '', hallName: data.halls.find(hall => hall.id === item.hallId)?.name || '', startTime: item.startTime, predictedRate: prediction.predictedRate, recommendedMovieId: replacement?.movieId, recommendedMovieTitle: replacement?.movieTitle, potentialLift: replacement ? Math.round((replacement.predictedRate - prediction.predictedRate) * 10) / 10 : 0, reason: '按当前历史需求估算的低上座风险' }
  }).filter(item => item.predictedRate < 35).slice(0, 30)
  const frequencyRecommendations = rankedMovies.slice(0, 15).map(item => {
    const samples = data.samples.filter(show => show.movieId === item.movieId)
    const weekly = future.filter(show => show.movieId === item.movieId).length / 2
    const action = item.predictedRate > 55 && weekly < 5 ? 'ADD' : item.predictedRate < 25 && weekly > 3 ? 'REDUCE' : 'KEEP'
    return { movieId: item.movieId, movieTitle: item.movieTitle, historicalRate: samples.length ? Math.round(samples.reduce((sum, show) => sum + show.rate, 0) / samples.length * 10) / 10 : null, historicalSamples: samples.length, currentWeeklyShowtimes: Math.round(weekly), action, suggestedChange: action === 'ADD' ? 1 : action === 'REDUCE' ? -1 : 0, reason: '基于需求估算与当前场次供给' }
  })
  const futurePredictions = future.map(show => predict(data, show.movieId, show.hallId, show.startTime).predictedRate)
  return { summary: { predictedAverageRate: futurePredictions.length ? Math.round(futurePredictions.reduce((a, b) => a + b, 0) / futurePredictions.length * 10) / 10 : 0, futureShowtimeCount: future.length, lowOccupancyCount: lowOccupancyShowtimes.length, actionableSuggestionCount: frequencyRecommendations.filter(item => item.action !== 'KEEP').length, modelConfidence: confidence(data.samples.length), historicalSamples: data.samples.length }, halls: data.halls.map(({ _id, ...hall }) => hall), heatmap, lowOccupancyShowtimes, goldenTimeRecommendations: rankedMovies.slice(0, 5), frequencyRecommendations, model: { name: '可解释加权历史模型', training: '使用已支付订单和已结束场次估算需求', weights: { 影片: 35, 全局: 20, 时段: 15, 影厅: 10, 星期: 10, 口碑: 10 } }, evaluation: { meanAbsoluteError: null, rootMeanSquaredError: null, trainingSampleCount: data.samples.length, testSampleCount: 0, protocol: '尚未完成时间外测试；界面只展示历史样本数，不给出未经检验的误差值。' }, aiAnalysis: analysis('依据历史售票和未来排片生成经营建议。', data.samples.length) }
}

async function optimize(db, body) {
  const started = Date.now()
  const cinemaId = numericId(body?.cinemaId)
  const date = String(body?.targetDate || '')
  assert(/^\d{4}-\d{2}-\d{2}$/.test(date) && millis(`${date}T00:00:00`) >= Date.now() - 86400000 && millis(`${date}T00:00:00`) <= Date.now() + 31 * 86400000, 400, '目标日期无效')
  const data = await basis(db, cinemaId)
  const selectedMovies = (body.movieIds?.length ? data.movies.filter(item => body.movieIds.includes(item.id)) : data.movies).filter(item => item.status === 1)
  const activeHalls = data.halls.filter(item => item.status === 1)
  assert(activeHalls.length && selectedMovies.length, 400, '没有可排片的影厅或影片')
  const target = Number(body.targetShowtimesPerHall)
  const maxPerMovie = Number(body.maxShowtimesPerMovie)
  const minDistinct = Number(body.minDistinctMovies)
  const goldenMinimum = Number(body.minGoldenShowtimesPerHall || 0)
  const cleaning = Math.max(15, Number(body.cleaningMinutes || 15))
  assert(Number.isSafeInteger(target) && target >= 1 && target <= 8 && Number.isSafeInteger(maxPerMovie) && maxPerMovie >= 1 && maxPerMovie <= 20 && Number.isSafeInteger(minDistinct) && minDistinct >= 1 && minDistinct <= 20 && Number.isSafeInteger(goldenMinimum) && goldenMinimum >= 0 && goldenMinimum <= 3 && goldenMinimum <= target, 400, '优化约束无效')
  assert(Number(body.revenueWeight) + Number(body.occupancyWeight) + Number(body.diversityWeight) === 100, 400, '目标权重之和必须为100')
  const current = data.shows.filter(item => item.startTime?.slice(0, 10) === date && item.status !== 2)
  const dayStart = millis(`${date}T00:00:00`)
  const adjacent = data.shows.filter(item => item.startTime?.slice(0, 10) !== date && item.status !== 2 && millis(item.startTime) < dayStart + 48 * 3600000 && millis(item.endTime) > dayStart - 4 * 3600000)
  const lockedIds = new Set(data.orders.filter(item => [0, 1].includes(item.status) && (item.status === 1 || millis(item.createTime) + 10 * 60000 > Date.now())).map(item => item.showtimeId))
  for (const show of current) if (millis(show.startTime) <= Date.now()) lockedIds.add(show.id)
  const locked = current.filter(item => lockedIds.has(item.id))
  const selected = locked.map(item => {
    const estimate = predict(data, item.movieId, item.hallId, item.startTime)
    return { sourceShowtimeId: item.id, movieId: item.movieId, movieTitle: data.movies.find(movie => movie.id === item.movieId)?.title || '', hallId: item.hallId, hallName: data.halls.find(hall => hall.id === item.hallId)?.name || '', startTime: item.startTime, endTime: item.endTime, unitPrice: item.basePrice, predictedRate: estimate.predictedRate, expectedAudience: estimate.expectedAudience, expectedRevenue: Math.round(estimate.expectedAudience * Number(item.basePrice) * 100) / 100, confidence: estimate.confidence, locked: true }
  })
  const counts = new Map()
  for (const item of selected) counts.set(item.movieId, (counts.get(item.movieId) || 0) + 1)
  let candidateVariables = 0
  for (const hall of activeHalls) {
    const times = []
    for (let hour = 9; hour <= 22; hour++) for (const minute of [0, 30]) times.push([hour, minute])
    times.sort((a, b) => Number(b[0] >= 18 && b[0] < 22) - Number(a[0] >= 18 && a[0] < 22) || a[0] - b[0] || a[1] - b[1])
    for (const [hour, minute] of times) {
        if (selected.filter(item => item.hallId === hall.id).length >= target) break
        const goldenCount = selected.filter(item => item.hallId === hall.id && Number(item.startTime.slice(11, 13)) >= 18 && Number(item.startTime.slice(11, 13)) < 22).length
        if (goldenCount < goldenMinimum && (hour < 18 || hour >= 22)) continue
        const startTime = `${date}T${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00+08:00`
        const startMs = millis(startTime)
        if (startMs < Date.now() + 15 * 60000) continue
        const remaining = activeHalls.reduce((sum, item) => sum + Math.max(0, target - selected.filter(show => show.hallId === item.id).length), 0)
        const diversityNeeded = Math.max(0, minDistinct - counts.size)
        const candidates = selectedMovies.filter(movie => (counts.get(movie.id) || 0) < maxPerMovie && (remaining > diversityNeeded || !counts.has(movie.id))).map(movie => {
          const endMs = startMs + Number(movie.duration || 120) * 60000
          const conflicts = [...selected, ...adjacent].some(item => item.hallId === hall.id && startMs < millis(item.endTime) + cleaning * 60000 && endMs + cleaning * 60000 > millis(item.startTime))
          if (conflicts) return null
          const estimate = predict(data, movie.id, hall.id, startTime)
          candidateVariables++
          const diversity = counts.has(movie.id) ? 0 : 100
          const score = Number(body.revenueWeight) * estimate.predictedRate * Number(hall.totalSeats || 0) * Number(movie.rating || 6) / 1000 + Number(body.occupancyWeight) * estimate.predictedRate + Number(body.diversityWeight) * diversity
          return { movie, endMs, estimate, score }
        }).filter(Boolean).sort((a, b) => b.score - a.score)
        if (!candidates.length) continue
        const choice = candidates[0]
        selected.push({ movieId: choice.movie.id, movieTitle: choice.movie.title, hallId: hall.id, hallName: hall.name, startTime, endTime: local(choice.endMs), unitPrice: Math.max(20, Math.round(Number(choice.movie.rating || 6) * 5)), predictedRate: choice.estimate.predictedRate, expectedAudience: choice.estimate.expectedAudience, expectedRevenue: Math.round(choice.estimate.expectedAudience * Math.max(20, Math.round(Number(choice.movie.rating || 6) * 5)) * 100) / 100, confidence: choice.estimate.confidence, locked: false })
        counts.set(choice.movie.id, (counts.get(choice.movie.id) || 0) + 1)
    }
  }
  const distinct = new Set(selected.map(item => item.movieId)).size
  const goldenSatisfied = activeHalls.every(hall => selected.filter(item => item.hallId === hall.id && Number(item.startTime.slice(11, 13)) >= 18 && Number(item.startTime.slice(11, 13)) < 22).length >= goldenMinimum)
  const feasible = selectedMovies.length >= minDistinct && distinct >= minDistinct && goldenSatisfied && [...counts.values()].every(value => value <= maxPerMovie) && activeHalls.every(hall => selected.filter(item => item.hallId === hall.id).length >= target)
  const manualRows = current.map(item => {
    const estimate = predict(data, item.movieId, item.hallId, item.startTime)
    return { ...item, expectedAudience: estimate.expectedAudience, expectedRevenue: estimate.expectedAudience * Number(item.basePrice) }
  })
  const manual = metrics(data, manualRows, '当前人工排片', Date.now() - started)
  const optimized = metrics(data, selected, '约束启发式方案', Date.now() - started)
  return { status: feasible ? 'FEASIBLE' : 'INFEASIBLE', statusText: feasible ? '找到可行排片方案' : '当前约束下未找到可行方案', reason: feasible ? undefined : '请放宽每厅场次、单片上限、影片多样性或黄金时段约束', solver: 'uniCloud Node.js 约束启发式', targetDate: date, objectiveValue: null, bestObjectiveBound: null, optimalityGap: null, solveTimeMs: Date.now() - started, candidateVariables, lockedShowtimes: locked.length, selectedShowtimes: feasible ? selected.sort((a, b) => a.startTime.localeCompare(b.startTime)) : [], comparisons: [manual, optimized], revenueLiftVsManual: manual.expectedRevenue ? Math.round((optimized.expectedRevenue / manual.expectedRevenue - 1) * 1000) / 10 : null, revenueLiftVsHeuristic: null, occupancyLiftVsManual: Math.round((optimized.occupancyRate - manual.occupancyRate) * 10) / 10, occupancyLiftVsHeuristic: null, constraints: ['保留已售场次', '每厅目标场次', '单片场次上限', '影片多样性', '清场间隔', '黄金时段'], weights: { revenue: body.revenueWeight, occupancy: body.occupancyWeight, diversity: body.diversityWeight }, notice: '这是启发式可行方案，未证明全局最优。结果仅供决策模拟，不会自动写入排片。', aiAnalysis: analysis('已根据场次约束和历史需求生成排片方案。', data.samples.length) }
}

async function handle(request, db) {
  requireUser(request, ['ADMIN', 'CINEMA_ADMIN'])
  const { method, path, body, query } = request
  scopeCinemaId(request, null)
  if (method === 'GET' && path === '/statistics/scheduling-insights') {
    const cinemaId = numericId(query.cinemaId)
    requireCinema(request, cinemaId)
    return success(await insights(db, cinemaId))
  }
  if (method === 'POST' && path === '/statistics/scheduling/predict') {
    requireCinema(request, numericId(body?.cinemaId))
    const data = await basis(db, numericId(body?.cinemaId))
    const result = predict(data, numericId(body.movieId), numericId(body.hallId), String(body.startTime))
    return success({ ...result, aiAnalysis: analysis(`预计上座率 ${result.predictedRate}% ，请结合实际售票情况调整。`, data.samples.length) })
  }
  if (method === 'POST' && path === '/statistics/scheduling/simulate') {
    const originalId = numericId(body?.originalShowtimeId)
    const found = (await db.collection('mts_showtime').where({ id: originalId }).limit(1).get()).data?.[0]
    assert(found, 404, '原场次不存在')
    requireCinema(request, found.cinemaId)
    const data = await basis(db, found.cinemaId)
    const hallId = body.targetHallId ? numericId(body.targetHallId) : found.hallId
    const startTime = String(body.targetStartTime || found.startTime)
    const original = predict(data, found.movieId, found.hallId, found.startTime)
    const adjusted = predict(data, numericId(body.targetMovieId), hallId, startTime)
    const occupancyLift = Math.round((adjusted.predictedRate - original.predictedRate) * 10) / 10
    return success({ original, adjusted, occupancyLift, expectedExtraAudience: adjusted.expectedAudience - original.expectedAudience, expectedRevenueLift: Math.round((adjusted.expectedAudience - original.expectedAudience) * Number(found.basePrice) * 100) / 100, recommendation: occupancyLift > 0 ? '模拟结果显示调整后需求较高，应用前仍需核对实际约束。' : '模拟结果未显示明确提升，建议保留原安排。', aiAnalysis: analysis('对原场次和调整方案进行同一历史模型下的比较。', data.samples.length) })
  }
  if (method === 'POST' && path === '/statistics/scheduling/optimize') {
    requireCinema(request, numericId(body?.cinemaId))
    return success(await optimize(db, body))
  }
  throw new ApiError(404, '接口不存在')
}

module.exports = { handle, predict, optimize }
