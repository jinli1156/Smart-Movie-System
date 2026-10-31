'use strict'
const test = require('node:test')
const assert = require('node:assert/strict')
const crypto = require('crypto')
const state = require('../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/ai-state')

test('private AI images are authenticated encrypted bytes, including against tampering', () => {
  const key = crypto.randomBytes(32).toString('hex'), input = Buffer.from('private image bytes')
  const encoded = state.encrypt(input, key)
  assert(!encoded.includes(input))
  assert.deepEqual(state.decrypt(encoded, key), input)
  const tampered = Buffer.from(encoded); tampered[tampered.length - 1] ^= 1
  assert.throws(() => state.decrypt(tampered, key))
  assert.throws(() => state.decrypt(encoded, crypto.randomBytes(32).toString('hex')))
  assert.throws(() => state.encrypt(input, ''), e => e.code === 503)
})
test('interrupted submissions stay uncertain; saved results resume without another paid call', () => {
  assert.equal(state.interruptedStatus({ status: 'SUBMITTING' }), 'SUBMIT_UNKNOWN')
  assert.equal(state.interruptedStatus({ status: 'PERSISTING', providerResult: 'saved-result' }), 'PERSISTING')
  assert.equal(state.interruptedStatus({ status: 'PLANNING' }), 'FAILED')
  assert.equal(state.interruptedStatus({ status: 'SUCCEEDED' }), 'SUCCEEDED')
})
test('private jobs hide existence from other accounts and quotas use Beijing midnight', () => {
  assert.throws(() => state.owned({ ownerId: 1 }, 2), e => e.code === 404)
  assert.equal(state.day(Date.parse('2026-10-31T16:00:00Z')), '2026-11-01')
})
test('worker state changes compare status and lease and forbid owner mutation', async () => {
  let filter, changes
  const db = { collection: () => ({ where(value) { filter = value; return { async update(value) { changes = value; return { updated: 1 } } } } }) }
  const job = { _id: 'job1', ownerId: 7, status: 'SUBMITTING', leaseToken: 'worker1' }
  const result = await state.transition(db, job, 'PERSISTING', { ownerId: 99, providerResult: 'url' })
  assert.deepEqual(filter, { _id: 'job1', status: 'SUBMITTING', leaseToken: 'worker1' })
  assert.equal(result.ownerId, 7); assert.equal(changes.ownerId, undefined)
  assert.equal(changes.providerResult, 'url')
  await assert.rejects(state.transition(db, job, 'QUEUED'), e => e.code === 409)
  db.collection = () => ({ where: () => ({ update: async () => ({ updated: 0 }) }) })
  await assert.rejects(state.transition(db, job, 'SUBMIT_UNKNOWN'), e => e.code === 409)
})
