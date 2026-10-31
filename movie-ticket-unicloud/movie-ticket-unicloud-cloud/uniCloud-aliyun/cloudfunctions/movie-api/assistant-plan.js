'use strict'

const { assert } = require('./core')

const FIELDS = new Set(['date', 'people', 'movieId', 'showtimeId', 'cinemaId', 'genre', 'minRating', 'maxDuration', 'startAfter', 'startBefore', 'endBefore', 'maxPrice', 'priceExclusive', 'maxTotal', 'cinemaKeyword', 'hallKeyword', 'usePoints', 'useCoupons', 'requireTogether', 'preference', 'naturalLanguage', 'excludedGenres', 'excludedMovieIds', 'scene', 'limit'])
const ENUMS = { scene: ['RELAXED', 'DATE', 'FAMILY', 'FRIENDS', 'SOLO'], preference: ['CENTER', 'QUIET', 'EASY_ACCESS', 'COUPLE', 'ACCESSIBLE'] }

function validate(criteria) {
  assert(criteria && typeof criteria === 'object' && !Array.isArray(criteria), 400, '观影条件无效')
  for (const [key, value] of Object.entries(criteria)) {
    assert(FIELDS.has(key), 400, '未知观影条件')
    if (['people', 'limit', 'maxDuration', 'movieId', 'showtimeId', 'cinemaId'].includes(key)) {
      const max = key === 'people' ? 5 : key === 'limit' ? 3 : key === 'maxDuration' ? 1000 : Number.MAX_SAFE_INTEGER
      assert(Number.isSafeInteger(value) && value >= 1 && value <= max, 400, '人数、方案数或 ID 无效')
    } else if (['maxPrice', 'maxTotal', 'minRating'].includes(key)) {
      assert(typeof value === 'number' && Number.isFinite(value) && value >= 0 && value <= (key === 'minRating' ? 10 : 100000) && Math.round(value * 100) === value * 100, 400, '预算或评分无效')
    } else if (['usePoints', 'useCoupons', 'requireTogether', 'priceExclusive'].includes(key)) {
      assert(typeof value === 'boolean', 400, '观影开关无效')
    } else if (key === 'excludedGenres' || key === 'excludedMovieIds') {
      assert(Array.isArray(value) && value.length <= (key === 'excludedGenres' ? 10 : 50), 400, '排除项过多')
      assert(value.every(item => key === 'excludedGenres' ? typeof item === 'string' && item.trim() && item.length <= 20 : Number.isSafeInteger(item) && item > 0), 400, '排除项无效')
    } else {
      assert(typeof value === 'string' && value.trim() && value.length <= 200, 400, '条件文本无效')
      if (key === 'date') assert(/^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0, 10) === value, 400, '日期无效')
      if (['startAfter', 'startBefore', 'endBefore'].includes(key)) assert(/^([01]\d|2[0-3]):[0-5]\d$/.test(value), 400, '时间使用 HH:mm')
      if (ENUMS[key]) assert(ENUMS[key].includes(value), 400, '枚举条件无效')
    }
  }
  if (criteria.startAfter && criteria.startBefore) assert(criteria.startAfter < criteria.startBefore, 400, '开场时间范围冲突')
}

function fromSaved(saved, preferences = {}) {
  const criteria = { ...(saved?.criteria || {}) }
  for (const field of ['excludedGenres', 'excludedMovieIds']) if (!(field in criteria) && preferences[field]) criteria[field] = preferences[field]
  validate(criteria)
  const options = Array.isArray(saved?.options) ? saved.options.filter(item => ['movie', 'showtime'].includes(item?.kind) && Number.isSafeInteger(item.id) && item.id > 0).slice(0, 6).map(item => ({ kind: item.kind, id: item.id, ...(Number.isSafeInteger(item.movieId) ? { movieId: item.movieId } : {}), title: String(item.title || '').slice(0, 100) })) : []
  return { criteria, options }
}

function patch(plan, input) {
  assert(input && typeof input === 'object' && !Array.isArray(input), 400, '请提供观影条件对象')
  assert(Object.keys(input).every(key => FIELDS.has(key) || ['reset', 'selectOption', 'rejectOptions'].includes(key)), 400, '未知观影条件')
  for (const flag of ['reset', 'rejectOptions']) if (flag in input) assert(typeof input[flag] === 'boolean', 400, '无效开关')
  const next = input.reset ? {} : { ...plan.criteria }
  if ('selectOption' in input) {
    assert(Number.isSafeInteger(input.selectOption) && input.selectOption >= 1 && input.selectOption <= plan.options.length, 400, '该编号没有对应候选')
    const selected = plan.options[input.selectOption - 1]
    delete next.showtimeId
    if (selected.kind === 'movie') next.movieId = selected.id
    else { next.showtimeId = selected.id; if (selected.movieId) next.movieId = selected.movieId }
  }
  if (input.rejectOptions) {
    next.excludedMovieIds = [...new Set([...(next.excludedMovieIds || []), ...plan.options.map(item => item.kind === 'movie' ? item.id : item.movieId).filter(Boolean)])]
    delete next.movieId
    delete next.showtimeId
  }
  for (const [key, value] of Object.entries(input)) if (FIELDS.has(key)) {
    if (value === null) delete next[key]
    else next[key] = value
  }
  if (('date' in input && next.date !== plan.criteria.date) || ('movieId' in input && next.movieId !== plan.criteria.movieId)) delete next.showtimeId
  validate(next)
  return { criteria: next, options: input.reset || input.rejectOptions ? [] : plan.options }
}

module.exports = { validate, fromSaved, patch }
