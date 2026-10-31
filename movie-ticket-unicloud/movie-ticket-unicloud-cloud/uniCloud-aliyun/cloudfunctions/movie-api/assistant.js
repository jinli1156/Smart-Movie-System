'use strict'

const crypto = require('crypto')
const { ApiError, assert, success, numericId, nextId, now, clean, requireUser } = require('./core')
const watch = require('./assistant-plan')
const assistantTools = require('./assistant-tools')
const readEnv = require('./read-env')

const PAGE_SIZE = 20
const MODEL = () => readEnv('DEEPSEEK_MODEL') || 'deepseek-flash'

async function ownConversation(db, id, userId) {
  const found = await db.collection('mts_assistant_conversation').where({ id, userId }).limit(1).get()
  assert(found.data?.length, 404, '对话不存在或已删除')
  return found.data[0]
}

function publicConversation(value) {
  return { id: value.id, title: value.title, updatedAt: value.updatedAt }
}

function validatedPreferences(value) {
  assert(value && typeof value === 'object' && !Array.isArray(value), 400, '偏好格式无效')
  assert(Object.keys(value).every(key => ['excludedGenres', 'excludedMovieIds'].includes(key)), 400, '仅支持排除类型和影片')
  const genres = value.excludedGenres || []
  const movies = value.excludedMovieIds || []
  assert(Array.isArray(genres) && genres.length <= 30 && genres.every(item => typeof item === 'string' && item.length <= 50), 400, '排除类型无效')
  assert(Array.isArray(movies) && movies.length <= 100 && movies.every(item => Number.isSafeInteger(item) && item > 0), 400, '排除影片无效')
  return { excludedGenres: [...new Set(genres)], excludedMovieIds: [...new Set(movies)] }
}

async function preference(db, userId) {
  const found = await db.collection('mts_assistant_preference').where({ userId }).limit(1).get()
  return found.data?.[0]?.preferences || {}
}

async function context(db) {
  const [movies, showtimes, cinemas] = await Promise.all([
    db.collection('mts_movie').where({ status: 1 }).orderBy('rating', 'desc').limit(15).get(),
    db.collection('mts_showtime').where({ status: 0, startTime: db.command.gte(now()) }).orderBy('startTime', 'asc').limit(30).get(),
    db.collection('mts_cinema').where({ status: 1 }).limit(30).get()
  ])
  const cinemaNames = new Map((cinemas.data || []).map(item => [item.id, item.name]))
  const movieNames = new Map((movies.data || []).map(item => [item.id, item.title]))
  const movieData = (movies.data || []).map(item => ({ id: item.id, title: item.title, genre: item.genre, rating: item.rating, duration: item.duration, description: String(item.description || '').slice(0, 120) }))
  const showtimeData = (showtimes.data || []).filter(item => Date.parse(item.startTime) > Date.now()).map(item => ({ id: item.id, movieId: item.movieId, movieTitle: movieNames.get(item.movieId), cinema: cinemaNames.get(item.cinemaId), startTime: item.startTime, price: item.basePrice }))
  return { movies: movieData, showtimes: showtimeData }
}

async function modelAnswer(question, history, data, preferences, watchPlan, request, db) {
  const key = readEnv('DEEPSEEK_API_KEY')
  if (!key) return { answer: 'AI 助手尚未配置，请联系管理员设置 DeepSeek API Key。', source: 'unavailable', toolResults: [], watchPlan }
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 48000)
  try {
    const messages = [
      { role: 'system', content: `你是电影购票助手。查询影片、影院、场次、座位、订单、价格时必须使用工具获取实时数据库结果，不要依据旧对话、目录摘要或猜测作答。查询前可用 update_watch_plan 更新本次观影条件。只提供工具证实的数据；如果工具失败，明确说明无法确认。绝不替用户支付或确认下单。回答用简洁中文。本次观影条件：${JSON.stringify(watchPlan.criteria)}。` },
      ...history, { role: 'user', content: question }
    ]
    const toolResults = []
    for (let round = 0; round < 3; round++) {
      const response = await fetch('https://api.deepseek.com/chat/completions', {
        method: 'POST', signal: controller.signal,
        headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ model: MODEL(), thinking: { type: 'disabled' }, stream: false, max_tokens: 1200, tools: assistantTools.definitions, messages })
      })
      if (!response.ok) throw new Error(`DeepSeek HTTP ${response.status}`)
      const payload = await response.json()
      const message = payload.choices?.[0]?.message
      assert(message, 502, 'AI 服务未返回消息')
      if (!message.tool_calls?.length) {
        assert(typeof message.content === 'string' && message.content.trim(), 502, 'AI 服务未返回内容')
        return { answer: message.content.trim(), source: 'model', toolResults, watchPlan }
      }
      assert(message.tool_calls.length <= 4, 502, 'AI 单轮查询过多')
      messages.push({ role: 'assistant', content: message.content || null, tool_calls: message.tool_calls })
      for (const call of message.tool_calls) {
        const executed = await assistantTools.execute(call.function.name, call.function.arguments, request, db, watchPlan)
        watchPlan = executed.plan
        toolResults.push(executed.result)
        messages.push({ role: 'tool', tool_call_id: call.id, content: JSON.stringify(executed.result).slice(0, 14000) })
      }
    }
    return { answer: '已查询到部分信息，但还需要缩小条件才能准确回答。请指定影片、日期或影院。', source: 'model', toolResults, watchPlan }
  } catch (error) {
    console.error('assistant model request failed', error.message)
    return { answer: 'AI 助手暂时无法回答，请稍后重试。', source: 'unavailable', toolResults: [], watchPlan }
  } finally { clearTimeout(timer) }
}

async function chat(request, db) {
  const body = request.body || {}
  const question = String(body.message || '').trim()
  assert(question.length > 0 && question.length <= 500, 400, '请输入不超过500字的问题')
  let conversation = null
  let history = []
  let preferences = {}
  let watchPlan
  if (request.user) {
    const userId = request.user.id
    preferences = await preference(db, userId)
    if (body.conversationId) conversation = await ownConversation(db, String(body.conversationId), userId)
    else {
      const id = crypto.randomUUID()
      conversation = { id, userId, title: question.replace(/\s+/g, ' ').slice(0, 60), updatedAt: now(), watchPlan: {} }
      const created = await db.collection('mts_assistant_conversation').add(conversation)
      conversation._id = created.id
    }
    const turns = await db.collection('mts_assistant_turn').where({ conversationId: conversation.id }).orderBy('id', 'desc').limit(5).get()
    history = (turns.data || []).reverse().filter(item => item.status === 'complete' && item.source === 'model').flatMap(item => [
      { role: 'user', content: String(item.question).slice(0, 4000) },
      { role: 'assistant', content: String(item.answer).slice(0, 4000) }
    ])
    if (body.clientMessageId) {
      const previous = await db.collection('mts_assistant_turn').where({ conversationId: conversation.id, requestId: String(body.clientMessageId) }).limit(1).get()
      if (previous.data?.length) {
        const turn = previous.data[0]
        assert(turn.question === question, 409, '消息标识已使用')
        return success({ conversationId: conversation.id, answer: turn.answer, source: turn.source, suggestions: turn.suggestions || [], toolResults: turn.toolResults || [], watchPlan: turn.watchPlan || {} })
      }
    }
    watchPlan = watch.fromSaved(conversation.watchPlan, preferences)
  } else {
    assert(!body.conversationId, 401, '请先登录后查看或保存对话')
    assert(!body.history || (Array.isArray(body.history) && body.history.length <= 10), 400, '对话历史无效')
    history = (body.history || []).filter(item => ['user', 'assistant'].includes(item.role) && typeof item.content === 'string').map(item => ({ role: item.role, content: item.content.slice(0, 4000) }))
    let guest = {}
    if (body.guestPlan) {
      assert(typeof body.guestPlan === 'string' && body.guestPlan.length <= 20000, 400, '观影条件过大')
      try { guest = JSON.parse(body.guestPlan) } catch (_) { throw new ApiError(400, '观影条件格式无效') }
    }
    watchPlan = watch.fromSaved(guest)
  }
  if (body.planPatch !== undefined) watchPlan = watch.patch(watchPlan, body.planPatch)
  const output = await modelAnswer(question, history, null, preferences, watchPlan, request, db)
  const result = { conversationId: conversation?.id, answer: output.answer, source: output.source, suggestions: [], toolResults: output.toolResults, watchPlan: output.watchPlan }
  if (conversation) {
    await db.collection('mts_assistant_turn').add({ id: await nextId(db, 'assistant_turn'), conversationId: conversation.id, requestId: body.clientMessageId || crypto.randomUUID(), question, answer: output.answer, source: output.source, status: 'complete', suggestions: [], toolResults: output.toolResults, watchPlan: result.watchPlan, createdAt: now() })
    await db.collection('mts_assistant_conversation').doc(conversation._id).update({ updatedAt: now(), watchPlan: result.watchPlan })
  }
  return success(result)
}

async function handle(request, db) {
  const { method, path, body } = request
  if (method === 'GET' && path === '/assistant/capabilities') return success({ configured: !!readEnv('DEEPSEEK_API_KEY'), model: MODEL(), vision: false, streaming: false })
  if (method === 'POST' && path === '/assistant/chat') return chat(request, db)
  if (method === 'GET' && path === '/assistant/preferences') return success(await preference(db, requireUser(request).id))
  if (method === 'PUT' && path === '/assistant/preferences') {
    const userId = requireUser(request).id
    const value = validatedPreferences(body)
    const collection = db.collection('mts_assistant_preference')
    const found = await collection.where({ userId }).limit(1).get()
    if (found.data?.length) await collection.doc(found.data[0]._id).update({ preferences: value, updatedAt: now() })
    else await collection.add({ userId, preferences: value, updatedAt: now() })
    return success(value)
  }
  if (method === 'GET' && path === '/assistant/conversations') {
    const userId = requireUser(request).id
    const page = Number(request.query.page || 1)
    assert(Number.isSafeInteger(page) && page >= 1 && page <= 10000, 400, '页码无效')
    const found = await db.collection('mts_assistant_conversation').where({ userId }).orderBy('updatedAt', 'desc').skip((page - 1) * PAGE_SIZE).limit(PAGE_SIZE + 1).get()
    return success({ items: (found.data || []).slice(0, PAGE_SIZE).map(publicConversation), hasMore: found.data.length > PAGE_SIZE, page })
  }
  const match = /^\/assistant\/conversations\/([0-9a-fA-F-]{36})$/.exec(path)
  if (match) {
    const userId = requireUser(request).id
    const conversation = await ownConversation(db, match[1], userId)
    if (method === 'DELETE') {
      const turns = await db.collection('mts_assistant_turn').where({ conversationId: conversation.id }).limit(1000).get()
      for (const turn of turns.data || []) await db.collection('mts_assistant_turn').doc(turn._id).remove()
      await db.collection('mts_assistant_conversation').doc(conversation._id).remove()
      return success()
    }
    if (method === 'GET') {
      const before = request.query.before ? numericId(request.query.before) : null
      const condition = { conversationId: conversation.id, ...(before ? { id: db.command.lt(before) } : {}) }
      const found = await db.collection('mts_assistant_turn').where(condition).orderBy('id', 'desc').limit(31).get()
      const turns = (found.data || []).slice(0, 30).reverse().map(item => ({ ...clean(item), createdAt: item.createdAt }))
      return success({ conversation: publicConversation(conversation), turns, hasMore: found.data.length > 30, watchPlan: conversation.watchPlan || {} })
    }
  }
  throw new ApiError(404, '接口尚未迁移')
}

module.exports = { handle, validatedPreferences }
