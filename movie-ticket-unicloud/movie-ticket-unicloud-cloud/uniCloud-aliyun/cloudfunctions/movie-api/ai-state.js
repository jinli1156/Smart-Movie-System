'use strict'

// Durable state shared by the cloud AI modules. No model call happens here.
const crypto = require('crypto')
const { assert } = require('./core')

const TERMINAL = new Set(['SUCCEEDED', 'FAILED', 'SUBMIT_UNKNOWN'])
const TRANSITIONS = {
  QUEUED: ['RUNNING'], RUNNING: ['PLANNING', 'FAILED'],
  PLANNING: ['SUBMITTING', 'FAILED'], SUBMITTING: ['PERSISTING', 'SUBMIT_UNKNOWN'],
  PERSISTING: ['SUCCEEDED', 'FAILED']
}
function key(...parts) { return crypto.createHash('sha256').update(JSON.stringify(parts)).digest('hex') }
function day(clock) { return new Date(clock + 8 * 3600000).toISOString().slice(0, 10) }
function object(result) { return Array.isArray(result.data) ? result.data[0] : result.data }
function owned(job, userId) {
  assert(job && job.ownerId === userId, 404, '任务不存在或无权访问')
  return job
}

async function enqueue(db, { ownerId, kind, requestId, input, dailyLimit, metadata = {} }, clock = Date.now()) {
  assert(Number.isSafeInteger(ownerId) && ownerId > 0, 400, '用户编号无效')
  assert(['POSTER', 'SOUVENIR', 'MARKETING_COPY', 'MARKETING_IMAGE'].includes(kind), 400, '任务类型无效')
  assert(typeof requestId === 'string' && requestId.length >= 1 && requestId.length <= 100, 400, '请求标识无效')
  assert(Number.isSafeInteger(dailyLimit) && dailyLimit > 0, 500, '任务额度未配置')
  const serialized = JSON.stringify(input)
  assert(serialized && Buffer.byteLength(serialized) <= 100000, 400, '任务内容过长或无效')
  const id = key(ownerId, kind, requestId), inputHash = key(serialized)
  const tx = await db.startTransaction()
  try {
    const existing = object(await tx.collection('mts_ai_job').doc(id).get())
    if (existing) {
      owned(existing, ownerId)
      assert(existing.inputHash === inputHash, 409, '请求内容已变化，请使用新的请求标识')
      await tx.commit()
      return existing
    }
    // Concurrent transactions reserve the same account/day document. Conflicts
    // propagate; callers replay their original request rather than invoke a model.
    const quotaId = key(ownerId, kind, day(clock))
    const quota = object(await tx.collection('mts_ai_quota').doc(quotaId).get())
    assert((quota?.used || 0) < dailyLimit, 429, '今天的创作次数已用完')
    if (quota) await tx.collection('mts_ai_quota').doc(quotaId).update({ used: quota.used + 1 })
    else await tx.collection('mts_ai_quota').add({ _id: quotaId, ownerId, kind, day: day(clock), used: 1 })
    const job = { ...metadata, _id: id, id, ownerId, kind, input, inputHash, status: 'QUEUED',
      attempts: 0, providerCalls: 0, createdAt: clock, updatedAt: clock, leaseUntil: 0, leaseToken: '' }
    await tx.collection('mts_ai_job').add(job)
    await tx.commit()
    return job
  } catch (error) {
    await tx.rollback().catch(() => {})
    throw error
  }
}

async function transition(db, job, status, patch = {}, clock = Date.now()) {
  assert(TRANSITIONS[job.status]?.includes(status), 409, '任务状态不允许此操作')
  // Never allow the worker payload to change ownership, input, or status itself.
  const permitted = ['providerResult', 'storageKey', 'errorMessage', 'leaseUntil', 'leaseToken']
  const update = Object.fromEntries(permitted.filter(k => Object.hasOwn(patch, k)).map(k => [k, patch[k]]))
  const result = await db.collection('mts_ai_job').where({ _id: job._id, status: job.status,
    leaseToken: job.leaseToken || '' }).update({ ...update, status, updatedAt: clock })
  assert(result.updated === 1, 409, '任务已被其他执行器处理')
  return { ...job, ...update, status, updatedAt: clock }
}

function interruptedStatus(job) {
  // A worker disappearing during submission must never cause another paid call.
  if (job.status === 'SUBMITTING') return 'SUBMIT_UNKNOWN'
  if (job.status === 'PERSISTING' && job.providerResult) return 'PERSISTING'
  return TERMINAL.has(job.status) ? job.status : 'FAILED'
}

function encrypt(bytes, hexKey) {
  assert(/^[a-f0-9]{64}$/i.test(hexKey || ''), 503, '尚未配置私有素材加密密钥')
  const iv = crypto.randomBytes(12)
  const cipher = crypto.createCipheriv('aes-256-gcm', Buffer.from(hexKey, 'hex'), iv)
  const data = Buffer.concat([cipher.update(bytes), cipher.final()])
  return Buffer.concat([Buffer.from('MTA1'), iv, cipher.getAuthTag(), data])
}
function decrypt(bytes, hexKey) {
  assert(Buffer.isBuffer(bytes) && bytes.length >= 32 && bytes.subarray(0, 4).toString() === 'MTA1', 502, '素材文件损坏')
  assert(/^[a-f0-9]{64}$/i.test(hexKey || ''), 503, '尚未配置私有素材加密密钥')
  const cipher = crypto.createDecipheriv('aes-256-gcm', Buffer.from(hexKey, 'hex'), bytes.subarray(4, 16))
  cipher.setAuthTag(bytes.subarray(16, 32))
  return Buffer.concat([cipher.update(bytes.subarray(32)), cipher.final()])
}

module.exports = { enqueue, transition, owned, interruptedStatus, encrypt, decrypt, day }
