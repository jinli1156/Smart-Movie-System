'use strict'

const { assert, numericId, requireUser, ApiError } = require('./core')

function scopeCinemaId(request, requested) {
  if (request.user?.role !== 'CINEMA_ADMIN') return requested == null || requested === '' ? null : numericId(requested)
  const assigned = request.user.cinemaId
  assert(Number.isSafeInteger(assigned) && assigned > 0, 403, '尚未分配影院，请联系平台管理员')
  assert(requested == null || requested === '' || numericId(requested) === assigned, 403, '无权管理其他影院的数据')
  return assigned
}

function requireCinema(request, cinemaId) {
  requireUser(request, ['ADMIN', 'CINEMA_ADMIN'])
  assert(cinemaId != null, 403, '无法确认数据所属影院')
  return scopeCinemaId(request, numericId(cinemaId))
}

async function requireOrder(request, db, order) {
  const user = requireUser(request)
  if (user.role === 'ADMIN') return
  if (user.role === 'CINEMA_ADMIN') {
    scopeCinemaId(request, null)
    const result = await db.collection('mts_showtime').where({ id: order.showtimeId }).limit(1).get()
    requireCinema(request, result.data?.[0]?.cinemaId)
    return
  }
  assert(order.userId === user.id, 403, '无权访问或操作该订单')
}

async function cinemaShows(db, cinemaId) {
  const rows = []
  for (let page = 0; page < 100; page++) {
    const result = await db.collection('mts_showtime').where({ cinemaId }).skip(page * 1000).limit(1000).get()
    rows.push(...(result.data || []))
    if ((result.data || []).length < 1000) return rows
  }
  throw new ApiError(503, '影院历史场次过多，请联系平台管理员')
}

async function orderFilter(request, db) {
  const cinemaId = scopeCinemaId(request, null)
  if (cinemaId == null) return {}
  const shows = await cinemaShows(db, cinemaId)
  // An empty cinema must produce zero matches, never remove the scope constraint.
  return { showtimeId: db.command.in(shows.length ? shows.map(item => item.id) : [-1]) }
}

module.exports = { scopeCinemaId, requireCinema, requireOrder, cinemaShows, orderFilter }
