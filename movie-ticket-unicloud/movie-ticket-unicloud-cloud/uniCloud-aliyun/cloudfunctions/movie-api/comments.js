'use strict'

const { ApiError, assert, success, numericId, now, clean, nextId, requireUser } = require('./core')
const { millis } = require('./orders')

async function watchedOrder(db, userId, movieId) {
  const paid = await db.collection('mts_order').where({ userId, status: 1 }).limit(1000).get()
  for (const order of paid.data || []) {
    const showtime = await db.collection('mts_showtime').where({ id: order.showtimeId }).limit(1).get()
    if (showtime.data?.[0]?.movieId === movieId && millis(showtime.data[0].endTime) < Date.now()) return order
  }
  return null
}

async function commentView(db, comment) {
  const result = await db.collection('mts_user').where({ id: comment.userId }).limit(1).get()
  const user = result.data?.[0]
  return { ...clean(comment), userName: user?.nickname || '', userAvatar: user?.avatar || '' }
}

async function refreshRating(db, movieId) {
  const comments = await db.collection('mts_comment').where({ movieId, deleted: 0 }).limit(1000).get()
  if (!comments.data?.length) return
  const rating = Math.round(comments.data.reduce((sum, item) => sum + Number(item.rating), 0) / comments.data.length * 20) / 10
  const movie = await db.collection('mts_movie').where({ id: movieId }).limit(1).get()
  if (movie.data?.length) await db.collection('mts_movie').doc(movie.data[0]._id).update({ rating, updateTime: now() })
}

async function handle(request, db) {
  const { method, path, query, body } = request
  if (method === 'GET' && /^\/comment\/movie\/\d+$/.test(path)) {
    const movieId = numericId(path.split('/')[3])
    const result = await db.collection('mts_comment').where({ movieId, deleted: 0 }).orderBy('createTime', 'desc').limit(1000).get()
    return success(await Promise.all((result.data || []).map(item => commentView(db, item))))
  }
  if (method === 'GET' && path === '/comment/eligibility') {
    const userId = requireUser(request).id
    const movieId = numericId(query.movieId)
    const existing = await db.collection('mts_comment').where({ userId, movieId, deleted: 0 }).limit(1).get()
    const watched = !!(await watchedOrder(db, userId, movieId))
    return success({ watched, commented: !!existing.data?.length, canComment: watched && !existing.data?.length, ownComment: existing.data?.length ? await commentView(db, existing.data[0]) : null })
  }
  if (method === 'POST' && path === '/comment') {
    const userId = requireUser(request).id
    const movieId = numericId(body?.movieId)
    const rating = Number(body?.rating)
    assert(Number.isInteger(rating) && rating >= 1 && rating <= 5, 400, '评分必须为1到5星')
    const existing = await db.collection('mts_comment').where({ userId, movieId, deleted: 0 }).count()
    assert(existing.total === 0, 409, '您已评价过该电影')
    const order = await watchedOrder(db, userId, movieId)
    assert(order, 403, '购票观影后才能评价')
    const comment = { id: await nextId(db, 'comment'), movieId, userId, orderId: order.id, rating, content: String(body.content || '').slice(0, 500), deleted: 0, createTime: now(), updateTime: now() }
    await db.collection('mts_comment').add(comment)
    await refreshRating(db, movieId)
    return success(await commentView(db, comment), '评价成功')
  }
  const match = /^\/comment\/(\d+)$/.exec(path)
  if (method === 'PUT' && match) {
    const userId = requireUser(request).id
    const rating = Number(body?.rating)
    assert(Number.isInteger(rating) && rating >= 1 && rating <= 5, 400, '评分必须为1到5星')
    const result = await db.collection('mts_comment').where({ id: numericId(match[1]), deleted: 0 }).limit(1).get()
    const comment = result.data?.[0]
    assert(comment, 404, '评价不存在')
    assert(comment.userId === userId, 403, '无权修改该评价')
    const update = { rating, content: String(body.content || '').slice(0, 500), updateTime: now() }
    await db.collection('mts_comment').doc(comment._id).update(update)
    await refreshRating(db, comment.movieId)
    return success(await commentView(db, { ...comment, ...update }), '评价修改成功')
  }
  throw new ApiError(404, '接口尚未迁移')
}

module.exports = { handle }
