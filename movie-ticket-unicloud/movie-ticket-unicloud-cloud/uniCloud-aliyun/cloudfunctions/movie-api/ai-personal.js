'use strict'
const { assert, success, requireUser, ApiError } = require('./core')
const ai = require('./ai-common')
const state = require('./ai-state')
const demand = require('./ai-demand')

async function owned(db, id, userId, kind) {
  const job = state.owned(await ai.get(db, 'mts_ai_job', id), userId)
  assert(job.kind === kind, 404, '任务不存在')
  return job
}
async function order(db, orderNo, userId) {
  const row = (await db.collection('mts_order').where({ orderNo, userId }).limit(1).get()).data?.[0]
  assert(row, 404, '订单不存在或无权访问')
  return row
}
async function createPoster(request, db, user) {
  const { fields, files } = await ai.multipart(request)
  let data
  try { data = JSON.parse(fields.data || files.find(f => f.name === 'data')?.bytes.toString('utf8') || '') } catch { throw new ApiError(400, '海报参数无效') }
  assert(typeof data.requestId === 'string' && /^[a-f0-9-]{36}$/i.test(data.requestId), 400, '请求标识无效')
  const input = { movieTitle: ai.text(data.movieTitle, 80, '电影主题'), prompt: ai.text(data.prompt, 1000, '海报创意'),
    style: data.style, ratio: data.ratio }
  assert(input.movieTitle || input.prompt, 400, '请填写主题或海报创意')
  assert(ai.styles.some(s => s.id === input.style) && ai.ratios.includes(input.ratio), 400, '风格或画幅无效')
  const photos = files.filter(f => f.name === 'photo')
  assert(photos.length <= 1 && files.every(f => ['data','photo'].includes(f.name)), 400, '仅支持一张人物照片')
  const photo = photos[0]
  if (photo) { ai.imageMime(photo.bytes); input.photoHash = ai.hash(photo.bytes.toString('base64')) }
  const cfg = ai.config(), id = ai.hash(user.id, 'POSTER', data.requestId)
  // Replay validation happens even when the provider has subsequently been disabled.
  const previous = await ai.get(db, 'mts_ai_job', id)
  if (previous) return state.enqueue(db, { ownerId: user.id, kind: 'POSTER', requestId: data.requestId, input, dailyLimit: cfg.posterLimit })
  assert(photo?cfg.referenceImageConfigured:cfg.imageConfigured, 503, photo?'Qwen图生图服务暂未配置':'智谱文生图服务暂未配置')
  assert(photo||input.prompt.length<=700,400,'文生图创意最多700字；添加参考照片后最多1000字')
  let portraitKey
  if (photo) portraitKey = await ai.putPrivate(photo.bytes, 'portrait')
  try {
    const job = await state.enqueue(db, { ownerId: user.id, kind: 'POSTER', requestId: data.requestId, input, dailyLimit: cfg.posterLimit, metadata: { portraitKey: portraitKey || null } })
    if (portraitKey && job.portraitKey !== portraitKey) await uniCloud.deleteFile({ fileList: [portraitKey] }).catch(() => {})
    return job
  } catch (e) {
    if (portraitKey) await uniCloud.deleteFile({ fileList: [portraitKey] }).catch(() => {})
    throw e
  }
}
async function createSouvenir(request, db, user, orderNo) {
  const o = await order(db, orderNo, user.id)
  assert(o.status === 1, 409, '完成支付后才可生成纪念票，已取消或退款订单不支持生成')
  const id = ai.hash(user.id, 'SOUVENIR', orderNo)
  const previous = await ai.get(db, 'mts_ai_job', id)
  if (previous) return request.body?.retry ? ai.retry(db, previous) : previous
  const cfg = ai.config()
  assert(cfg.imageConfigured && cfg.textConfigured, 503, 'AI纪念票服务暂未配置')
  const show = (await db.collection('mts_showtime').where({ id: o.showtimeId }).limit(1).get()).data?.[0]
  assert(show, 409, '订单场次资料不完整')
  const [movie, cinema, hall] = await Promise.all(['movie','cinema','hall'].map(async name =>
    (await db.collection('mts_' + name).where({ id: show[name + 'Id'] }).limit(1).get()).data?.[0]))
  assert(movie?.title, 409, '订单影片资料不完整')
  const facts = { movieTitle: movie.title, cinemaName: cinema?.name || '', hallName: hall?.name || '', showtime: show.startTime,
    genre: movie.genre || '', description: movie.description || '', seats: o.seats || '' }
  return state.enqueue(db, { ownerId: user.id, kind: 'SOUVENIR', requestId: orderNo,
    input: { orderNo, orderId: o.id, facts, note: ai.text(request.body?.note, 120, '观影心情'), style: 'STARFIELD', ratio: '3:4' }, dailyLimit: cfg.souvenirLimit })
}
async function handle(request, db) {
  const user = requireUser(request), { path, method } = request, cfg = ai.config()
  if (path === '/posters/capabilities') return success({ available: cfg.imageConfigured || cfg.referenceImageConfigured, photoSupported: cfg.referenceImageConfigured, executionMode: cfg.executionMode,
    styles: ai.styles, dailyLimit: cfg.posterLimit, remaining: Math.max(0, cfg.posterLimit - await ai.used(db, user.id, 'POSTER')),
    imageProvider: 'zhipu', imageModel: cfg.textImageModel, referenceImageProvider: 'qwen-images', referenceImageModel: 'qwen-image-3.0', message: cfg.imageConfigured || cfg.referenceImageConfigured ? '按需生成；已提交图片离开后继续创作，请在24小时内回来查看并保存' : 'AI海报服务暂未开放' })
  if (path === '/souvenirs/capabilities') return success({ available: cfg.imageConfigured && cfg.textConfigured, executionMode: cfg.executionMode, theme: '星空科幻', dailyLimit: cfg.souvenirLimit, message: '按需生成，请在24小时内回来查看并保存' })
  if (path === '/posters' && method === 'POST') { const job=await createPoster(request,db,user);await demand.advance(db,{ownerId:user.id,id:job.id});return success(ai.taskView(await ai.get(db,'mts_ai_job',job.id))) }
  if (path === '/posters' && method === 'GET') {
    await demand.advance(db,{ownerId:user.id,kind:'POSTER'})
    const jobs = await db.collection('mts_ai_job').where({ ownerId: user.id, kind: 'POSTER' }).orderBy('createdAt','desc').limit(50).get()
    return success(jobs.data.map(ai.taskView))
  }
  let match = path.match(/^\/souvenirs\/orders\/([^/]+)$/)
  if (match) {
    const orderNo = decodeURIComponent(match[1])
    if (method === 'POST') {const job=await createSouvenir(request,db,user,orderNo);await demand.advance(db,{ownerId:user.id,id:job.id});return success(ai.taskView(await ai.get(db,'mts_ai_job',job.id)))}
    if (method === 'GET') {
      await order(db, orderNo, user.id)
      await demand.advance(db,{ownerId:user.id,id:ai.hash(user.id,'SOUVENIR',orderNo)})
      const job = await ai.get(db, 'mts_ai_job', ai.hash(user.id, 'SOUVENIR', orderNo))
      return success(job ? [ai.taskView(job)] : [])
    }
  }
  match = path.match(/^\/(posters|souvenirs)\/([a-f0-9]{64})(?:\/(file|retry))?$/)
  if (match) {
    const job = await owned(db, match[2], user.id, match[1] === 'posters' ? 'POSTER' : 'SOUVENIR')
    if (match[3] === 'file' && method === 'GET') {
      assert(job.status === 'SUCCEEDED', 409, '图片仍在创作中')
      return ai.fileResponse(await ai.readPrivate(job.storageKey), request.query)
    }
    if (match[3] === 'retry' && method === 'POST') {await ai.retry(db,job);await demand.advance(db,{ownerId:user.id,id:job.id});return success(ai.taskView(await ai.get(db,'mts_ai_job',job.id)))}
    if (!match[3] && method === 'GET') {await demand.advance(db,{ownerId:user.id,id:job.id});return success(ai.taskView(await ai.get(db,'mts_ai_job',job.id)))}
  }
  throw new ApiError(404, '接口不存在')
}
module.exports = { handle, createSouvenir }
