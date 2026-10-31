'use strict'

const crypto = require('crypto')
const { Readable } = require('stream')
const Busboy = require('busboy')
const { assert, ApiError } = require('./core')
const env = require('./read-env')
const state = require('./ai-state')
const RELEASE = 'ai-cloud-20261031'
const styles = [
  ['CYBERPUNK', '赛博朋克', '霓虹都市、机械装甲与未来光影'], ['FANTASY', '东方神话', '流云烈焰与东方奇境'],
  ['SCIFI', '星际科幻', '深空星云与宇宙航行'], ['ANIME', '日系动画', '明亮色彩与青春故事'],
  ['RETRO', '复古胶片', '胶片颗粒与经典构图'], ['SUSPENSE', '悬疑电影', '明暗对照与迷雾']
].map(([id, name, description]) => ({ id, name, description }))
const ratios = ['3:4', '1:1', '16:9']
function hash(...values) { return crypto.createHash('sha256').update(JSON.stringify(values)).digest('hex') }
function object(result) { return Array.isArray(result.data) ? result.data[0] : result.data }
async function get(db, table, id) { return object(await db.collection(table).doc(id).get()) }
function text(value, max, label, required = false) {
  assert(value == null || typeof value === 'string', 400, `${label}格式无效`)
  const out = (value || '').trim()
  assert(out.length <= max && (!required || out.length > 0), 400, `请检查${label}（最多${max}字）`)
  return out
}
function config() {
  const imageKey = env('AI_IMAGE_KEY'), encryptionKey = env('AI_ASSET_KEY'), textImageKey=env('AI_TEXT_IMAGE_KEY')
  return { imageKey, encryptionKey, executionMode: env('AI_EXECUTION_MODE') || 'demand', provider: env('AI_IMAGE_PROVIDER') || 'qwen-images',
    model: env('AI_IMAGE_MODEL') || 'qwen-image-3.0', base: env('AI_IMAGE_BASE') || 'https://dashscope.aliyuncs.com/api/v1',
    textImageKey, textImageModel: env('AI_TEXT_IMAGE_MODEL') || 'glm-image', textImageBase: env('AI_TEXT_IMAGE_BASE') || 'https://open.bigmodel.cn/api/paas/v4',
    imageConfigured: !!textImageKey && /^[a-f0-9]{64}$/i.test(encryptionKey || ''), referenceImageConfigured: !!imageKey && /^[a-f0-9]{64}$/i.test(encryptionKey || ''), textConfigured: !!env('DEEPSEEK_API_KEY'), visionConfigured: !!env('AI_VISION_KEY'),
    posterLimit: 5, souvenirLimit: 5, marketingLimit: 60, batchLimit: 18 }
}
function imageSettings(job) {
  const c=config()
  return job.portraitKey || job.input?.photoHash
    ? {provider:'qwen-images',model:'qwen-image-3.0',base:c.base,key:c.imageKey,configured:c.referenceImageConfigured}
    : {provider:'zhipu',model:c.textImageModel,base:c.textImageBase,key:c.textImageKey,configured:c.imageConfigured}
}
function binary(bytes, mime = 'image/png', filename) {
  return { mpserverlessComposedResponse: true, isBase64Encoded: true, statusCode: 200,
    headers: { 'Content-Type': mime, 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff',
      ...(filename ? { 'Content-Disposition': `attachment; filename="${filename}"` } : {}) }, body: bytes.toString('base64') }
}
const FILE_CHUNK = 512 * 1024
function fileResponse(bytes, query = {}) {
  const parts = Math.ceil(bytes.length / FILE_CHUNK)
  if (query.part !== undefined) {
    const part = Number(query.part)
    assert(Number.isInteger(part) && part >= 0 && part < parts, 400, '文件分段无效')
    return binary(bytes.subarray(part * FILE_CHUNK, (part + 1) * FILE_CHUNK), 'application/octet-stream')
  }
  if (parts > 1) return { code: 200, message: '分段下载', data: { parts, size: bytes.length, mime: 'image/png' } }
  return binary(bytes)
}
async function multipart(request) {
  // Explicit JSON/base64 transport avoids gateway-specific multipart rewriting.
  if ((request.headers?.['content-type'] || '').includes('application/json')) {
    const input = request.body
    assert(input && typeof input === 'object' && !Array.isArray(input) && Buffer.byteLength(JSON.stringify(input)) <= 1900 * 1024, 413, '上传资料过大或格式无效')
    const fields = input.fields || {}, incoming = input.files || []
    assert(fields && typeof fields === 'object' && !Array.isArray(fields) && Object.keys(fields).length <= 8 && Object.values(fields).every(v => typeof v === 'string' && v.length <= 40000), 400, '资料字段无效')
    assert(Array.isArray(incoming) && incoming.length <= 3, 400, '最多上传3个文件')
    const files = incoming.map(file => {
      assert(typeof file.name === 'string' && typeof file.filename === 'string' && file.filename.length <= 200 && typeof file.base64 === 'string' && /^[A-Za-z0-9+/]*={0,2}$/.test(file.base64), 400, '文件编码无效')
      const bytes = Buffer.from(file.base64, 'base64')
      assert(bytes.length > 0 && bytes.length <= 1400 * 1024 && bytes.toString('base64') === file.base64, 413, '单文件须小于1.4MB且编码完整')
      return { name: file.name, filename: file.filename, mime: String(file.mime || 'application/octet-stream'), bytes }
    })
    return { fields, files }
  }
  assert((request.headers?.['content-type'] || '').startsWith('multipart/form-data;'), 400, '请上传资料')
  const bytes = Buffer.isBuffer(request.body) ? request.body : Buffer.from(request.body || '', 'binary')
  assert(bytes.length <= 1900 * 1024, 413, '云端单次资料合计须小于1.8MB')
  return new Promise((resolve, reject) => {
    const fields = {}, files = []
    let exceeded = false
    const parser = Busboy({ headers: request.headers, limits: { files: 3, fileSize: 1400 * 1024, fields: 8, fieldSize: 160000, parts: 12 } })
    parser.on('field', (name, value, info) => { fields[name] = value; if (info.valueTruncated) exceeded = true })
    parser.on('file', (name, stream, info) => {
      const chunks = []
      stream.on('data', b => chunks.push(b)); stream.on('limit', () => { exceeded = true })
      stream.on('end', () => files.push({ name, filename: info.filename, mime: info.mimeType, bytes: Buffer.concat(chunks) }))
    })
    for (const event of ['filesLimit', 'fieldsLimit', 'partsLimit']) parser.on(event, () => { exceeded = true })
    parser.on('error', reject)
    parser.on('finish', () => {
      if (exceeded) reject(new ApiError(413, '单文件限1.4MB，资料文本限40000字，最多3个文件'))
      else resolve({ fields, files })
    })
    Readable.from(bytes).pipe(parser)
  })
}
function imageMime(bytes) {
  if (bytes.subarray(0, 8).equals(Buffer.from([137,80,78,71,13,10,26,10]))) return 'image/png'
  if (bytes[0] === 255 && bytes[1] === 216 && bytes[2] === 255) return 'image/jpeg'
  throw new ApiError(400, '图片内容无效，仅支持PNG或JPEG')
}
async function putPrivate(bytes, suffix = 'bin') {
  const encrypted = state.encrypt(bytes, config().encryptionKey)
  const result = await uniCloud.uploadFile({ cloudPath: `movie-ai-private/${crypto.randomUUID()}.${suffix}.enc`, cloudPathAsRealPath: true, fileContent: encrypted })
  assert(result.fileID, 502, '素材保存失败')
  return result.fileID
}
async function readPrivate(fileID) {
  assert(typeof fileID === 'string' && fileID.length > 0, 404, '素材不存在')
  if (typeof uniCloud.getTempFileURL === 'function') {
    // Aliyun does not implement the server-side downloadFile API.
    const result = await uniCloud.getTempFileURL({ fileList: [fileID] })
    const url = new URL(result.fileList?.[0]?.tempFileURL || '')
    assert(url.protocol === 'https:' && !url.username && !url.password && (url.hostname.endsWith('.bspapp.com') || url.hostname.endsWith('.aliyuncs.com')), 502, '云存储下载地址无效')
    const response = await fetch(url, { redirect: 'error', signal: AbortSignal.timeout(60000) })
    assert(response.ok && Number(response.headers.get('content-length') || 0) <= 21 * 1024 * 1024, 502, '素材下载失败或过大')
    let size = 0; const chunks = []
    for await (const chunk of response.body) { size += chunk.length; assert(size <= 21 * 1024 * 1024, 502, '素材过大'); chunks.push(Buffer.from(chunk)) }
    return state.decrypt(Buffer.concat(chunks), config().encryptionKey)
  }
  const downloaded = await uniCloud.downloadFile({ fileID })
  let bytes = downloaded.fileContent
  if (!bytes && downloaded.tempFilePath) bytes = require('fs').readFileSync(downloaded.tempFilePath)
  return state.decrypt(Buffer.from(bytes || []), config().encryptionKey)
}
async function jsonModel(system, data, { vision = false } = {}) {
  const key = vision ? env('AI_VISION_KEY') : env('DEEPSEEK_API_KEY')
  assert(key, 503, 'AI文字服务尚未配置')
  const response = await fetch(vision ? (env('AI_VISION_BASE') || 'https://open.bigmodel.cn/api/paas/v4').replace(/\/+$/, '') + '/chat/completions' : 'https://api.deepseek.com/chat/completions', {
    method: 'POST', signal: AbortSignal.timeout(75000), headers: { Authorization: `Bearer ${key}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ model: vision ? env('AI_VISION_MODEL') || 'glm-4.6v-flash' : env('DEEPSEEK_MODEL') || 'deepseek-flash',
      thinking: { type: 'disabled' }, stream: false, max_tokens: 7000,
      ...(vision ? {} : { response_format: { type: 'json_object' } }), messages: [{ role: 'system', content: system }, { role: 'user', content: typeof data === 'string' || Array.isArray(data) ? data : JSON.stringify(data) }] })
  })
  let payload
  if (vision && !response.ok && [404,429,500,502,503,504].includes(response.status) && config().provider === 'qwen-images' && config().imageKey) {
    const content = Array.isArray(data) ? data.map(item => item.type === 'image_url' ? { image: item.image_url.url } : { text: item.text }) : [{ text: JSON.stringify(data) }]
    const fallback = await fetch(config().base.replace(/\/+$/, '') + '/services/aigc/multimodal-generation/generation', {
      method: 'POST', signal: AbortSignal.timeout(75000), headers: { Authorization: `Bearer ${config().imageKey}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ model: 'qwen3-vl-plus', input: { messages: [{ role: 'system', content: [{ text: system }] }, { role: 'user', content }] }, parameters: { max_tokens: 7000, enable_thinking: false, result_format: 'message' } })
    })
    assert(fallback.ok, 502, `AI图文服务请求失败（HTTP ${fallback.status}）`)
    const native = await fallback.json(), choice = native.output?.choices?.[0]
    payload = { choices: [{ ...choice, message: { content: choice?.message?.content?.filter(c => c.text).map(c => c.text).join('') } }] }
  } else {
    assert(response.ok, 502, `AI文字服务请求失败（HTTP ${response.status}）`)
    payload = await response.json()
  }
  const choice = payload.choices?.[0], content = choice?.message?.content
  assert(!choice?.finish_reason || choice.finish_reason === 'stop', 502, 'AI结果未完整返回，请减少资料后重试')
  assert(typeof content === 'string' && content.length <= 100000, 502, 'AI返回内容无效')
  try { return JSON.parse(content.replace(/^```(?:json)?\s*|\s*```$/g, '')) } catch { throw new ApiError(502, 'AI未返回有效JSON，请重试') }
}
async function used(db, ownerId, kind) {
  const quota = await get(db, 'mts_ai_quota', hash(ownerId, kind, state.day(Date.now())))
  return quota?.used || 0
}
function taskView(job) {
  const i = job.input
  return { id: job.id, movieTitle: i.movieTitle || '', style: i.style || 'STARFIELD', prompt: i.prompt || '', ratio: i.ratio || '3:4',
    imageProvider: job.imageProvider || job.providerTask?.provider || null, imageModel: job.imageModel || job.providerTask?.model || null,
    hasPortrait: !!i.photoHash, note: i.note || '', facts: i.facts || {}, headline: job.result?.headline || null, caption: job.result?.caption || null,
    status: ['QUEUED','SUCCEEDED','FAILED'].includes(job.status) ? job.status : job.status === 'SUBMIT_UNKNOWN' ? 'FAILED' : 'RUNNING',
    errorMessage: job.errorMessage || null, retryable: (job.status === 'FAILED' || job.status === 'PERSISTING') && (!!job.providerResult || !job.providerCalls) && job.attempts < 3,
    createdAt: new Date(job.createdAt + 8 * 3600000).toISOString().replace('Z', '+08:00') }
}
async function retry(db, job, acknowledge = false) {
  if (['QUEUED','RUNNING','PLANNING','SUBMITTING','GENERATING','SUCCEEDED'].includes(job.status)) return job
  assert(!job.providerTask || job.providerResult, 409, '已有模型任务编号，请核实结果，不能重复生成')
  assert(job.attempts < 3 && (job.providerResult || !job.providerCalls || job.status === 'SUBMIT_UNKNOWN' && acknowledge), 409, '生成结果尚未确定，不能重复提交，请先核实')
  const result = await db.collection('mts_ai_job').where({ _id: job._id, status: job.status, leaseToken: job.leaseToken }).update({
    status: 'QUEUED', errorMessage: null, attempts: job.attempts + 1, updatedAt: Date.now(), leaseToken: '', leaseUntil: 0 })
  assert(result.updated === 1, 409, '任务状态已变化，请刷新')
  return get(db, 'mts_ai_job', job._id)
}
module.exports = { RELEASE, styles, ratios, config, imageSettings, text, object, hash, get, binary, fileResponse, multipart, imageMime, putPrivate, readPrivate, jsonModel, used, taskView, retry }
