'use strict'

// Run with `node scripts/generate-schemas.js` from movie-ticket-unicloud.
// Schemas deny clientDB access; all authorization happens in movie-api.
const fs = require('fs')
const path = require('path')

const output = path.join(__dirname, '..', 'movie-ticket-unicloud-cloud', 'uniCloud-aliyun', 'database')
const collections = {
  mts_ai_job: { unique: [['id']], indexes: [['status','createdAt'],['status','leaseUntil'],['ownerId','kind','createdAt'],['campaignId','createdAt']], fields: { id:'string',ownerId:'number',kind:'string',status:'string',inputHash:'string' } },
  mts_ai_quota: { unique: [], indexes: [['ownerId','kind','day']], fields: {ownerId:'number',kind:'string',day:'string',used:'number'} },
  mts_ai_lock: {unique:[],indexes:[],fields:{token:'string',expiresAt:'number'}},
  mts_listing_draft: {unique:[['id']],indexes:[['ownerId','updatedAt'],['status','updatedAt'],['batchId']],fields:{id:'string',kind:'string',status:'string',ownerId:'number',version:'number'}},
  mts_marketing: {unique:[['id']],indexes:[['campaignId','type'],['type','cinemaId','createdAt'],['type','slot','status']],fields:{id:'string',type:'string',cinemaId:'number'}},
  mts_user: { unique: [['id'], ['username'], ['phone']], indexes: [['role', 'status'], ['cinemaId']], fields: { id: 'number', username: 'string', password: 'string', nickname: 'string', role: 'string', cinemaId: ['number', 'null'], status: 'number', points: 'number' } },
  mts_cinema: { unique: [['id']], indexes: [['status'], ['name']], fields: { id: 'number', name: 'string', address: 'string', status: 'number' } },
  mts_hall: { unique: [['id']], indexes: [['cinemaId', 'status']], fields: { id: 'number', cinemaId: 'number', name: 'string', status: 'number' } },
  mts_movie: { unique: [['id']], indexes: [['status'], ['title'], ['releaseDate']], fields: { id: 'number', title: 'string', duration: 'number', status: 'number' } },
  mts_showtime: { unique: [['id']], indexes: [['movieId', 'cinemaId', 'startTime'], ['hallId', 'startTime'], ['status']], fields: { id: 'number', movieId: 'number', cinemaId: 'number', hallId: 'number', startTime: 'string', status: 'number' } },
  mts_order: { unique: [['id'], ['orderNo'], ['ticketCode'], ['assistantDraftId']], indexes: [['userId', 'createTime'], ['showtimeId', 'status']], fields: { id: 'number', orderNo: 'string', userId: 'number', showtimeId: 'number', status: 'number', assistantDraftId: 'string' } },
  mts_user_coupon: { unique: [['id']], indexes: [['userId', 'status'], ['expireTime']], fields: { id: 'number', userId: 'number', status: 'number' } },
  mts_comment: { unique: [['id'], ['userId', 'movieId']], indexes: [['movieId'], ['orderId']], fields: { id: 'number', userId: 'number', movieId: 'number', orderId: 'number' } },
  mts_behavior: { unique: [['id'], ['userId', 'movieId', 'behaviorType']], indexes: [['userId', 'active'], ['movieId', 'behaviorType']], fields: { id: 'number', userId: 'number', movieId: 'number', behaviorType: 'string' } },
  mts_coupon_code: { unique: [['id'], ['code']], indexes: [['status', 'validFrom']], fields: { id: 'number', code: 'string', status: 'number' } },
  mts_coupon_redemption: { unique: [['id'], ['couponCodeId', 'userId']], indexes: [['userId'], ['couponCodeId']], fields: { id: 'number', couponCodeId: 'number', userId: 'number' } },
  mts_assistant_conversation: { unique: [['id']], indexes: [['userId', 'updatedAt']], fields: { id: 'string', userId: 'number' } },
  mts_assistant_turn: { unique: [['id'], ['conversationId', 'requestId']], indexes: [['conversationId', 'createdAt']], fields: { id: 'number', conversationId: 'string' } },
  mts_assistant_preference: { unique: [['userId']], indexes: [], fields: { userId: 'number' } },
  mts_assistant_checkout: { unique: [['id']], indexes: [['userId', 'expiresAt']], fields: { id: 'string', userId: 'number' } },
  mts_scheduling_draft: { unique: [['id'], ['userId', 'cinemaId', 'targetDate']], indexes: [['userId', 'savedAt']], fields: { id: 'string', userId: 'number', cinemaId: 'number', targetDate: 'string' } },
  mts_seat_hold: { unique: [['showtimeId', 'seat']], indexes: [['orderNo'], ['expiresAt']], fields: { showtimeId: 'number', seat: 'string', orderNo: 'string' } },
  mts_captcha: { unique: [], indexes: [['expiresAt']], fields: { digest: 'string', used: 'bool', expiresAt: 'number' } },
  mts_counter: { unique: [], indexes: [], fields: { seq: 'number' } }
}

function indexSpec(fields, unique) {
  return {
    IndexName: `${fields.join('_')}_${unique ? 'unique' : 'idx'}`,
    MgoKeySchema: {
      MgoIndexKeys: fields.map(field => ({ Name: field, Direction: '1' })),
      MgoIsUnique: unique,
      MgoIsSparse: unique && fields.some(field => ['phone', 'ticketCode', 'assistantDraftId'].includes(field))
    }
  }
}

fs.mkdirSync(output, { recursive: true })
for (const [name, spec] of Object.entries(collections)) {
  const schema = {
    bsonType: 'object',
    description: `Smart Movie System: ${name}`,
    permission: { read: false, create: false, update: false, delete: false, count: false },
    properties: Object.fromEntries(Object.entries(spec.fields).map(([field, bsonType]) => [field, { bsonType }]))
  }
  fs.writeFileSync(path.join(output, `${name}.schema.json`), JSON.stringify(schema, null, 2) + '\n')
  const indexes = [...spec.unique.map(fields => indexSpec(fields, true)), ...spec.indexes.map(fields => indexSpec(fields, false))]
  if (indexes.length) fs.writeFileSync(path.join(output, `${name}.index.json`), JSON.stringify(indexes, null, 2) + '\n')
}
console.log(`Generated ${Object.keys(collections).length} collection schemas in ${output}`)
