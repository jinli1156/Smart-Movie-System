'use strict'

const core = require('./core')
const catalog = require('./catalog')
const auth = require('./auth')
const orders = require('./orders')
const coupons = require('./coupons')
const comments = require('./comments')
const assistant = require('./assistant')
const recommend = require('./recommend')
const pricing = require('./pricing')
const uploads = require('./uploads')
const statistics = require('./statistics')
const scheduling = require('./scheduling')
const seats = require('./seats')
const checkout = require('./checkout')
const schedulingIntelligence = require('./scheduling-intelligence')
const ai = require('./ai-common')
const personal = require('./ai-personal')
const marketing = require('./ai-marketing')
const listing = require('./listing')

exports.main = async (event, context) => {
  if(context?.SOURCE==='server'&&!event.httpMethod){
    // A CLI invocation performs a read-only internal wiring check, never model probes.
    const result=await uniCloud.callFunction({name:'movie-ai-worker',data:{action:'advance',filter:{ownerId:-1,kind:'POSTER'}}})
    return {mode:ai.config().executionMode,worker:result.result}
  }
  let request
  try {
    request = core.parseEvent(event)
    const origin = request.headers.origin || ''
    if (request.method === 'OPTIONS') return core.response('', origin, 204)
    request.user = core.verifyToken(request.headers.authorization)
    if (request.method === 'GET' && request.path === '/health') {
      return core.response(core.success({ status: 'ok', release: ai.RELEASE }), origin)
    }
    const db = uniCloud.database()
    if (request.user) {
      const found = await db.collection('mts_user').where({ id: request.user.id, status: 1, deleted: 0 }).limit(1).get()
      request.user = found.data?.length ? { id: found.data[0].id, role: found.data[0].role, cinemaId: found.data[0].cinemaId ?? null } : null
    }
    const result = /^\/(posters|souvenirs)(\/|$)/.test(request.path)
      ? await personal.handle(request, db)
      : /^\/(admin\/marketing|marketing)(\/|$)/.test(request.path)
      ? await marketing.handle(request, db)
      : request.path.startsWith('/listing/')
      ? await listing.handle(request, db)
      : request.path.startsWith('/assistant/checkout/')
      ? await checkout.handle(request, db)
      : request.path.startsWith('/assistant/')
      ? await assistant.handle(request, db)
      : request.path.startsWith('/recommend/')
        ? await recommend.handle(request, db)
      : request.path.startsWith('/pricing/')
        ? await pricing.handle(request, db)
      : request.path.startsWith('/upload/')
        ? await uploads.handle(request, db)
      : request.path.startsWith('/statistics/scheduling/')
        ? request.path.endsWith('/predict') || request.path.endsWith('/simulate') || request.path.endsWith('/optimize')
          ? await schedulingIntelligence.handle(request, db)
          : await scheduling.handle(request, db)
      : request.path === '/statistics/scheduling-insights'
        ? await schedulingIntelligence.handle(request, db)
      : request.path.startsWith('/statistics/')
        ? await statistics.handle(request, db)
      : /^\/movie\/box-office\//.test(request.path)
        ? await statistics.handle(request, db)
      : /^\/movie\/\d+\/insight$/.test(request.path)
        ? await statistics.handle(request, db)
      : /^\/showtime\/\d+\/seat-recommendation$/.test(request.path)
        ? await seats.handle(request, db)
      : request.path.startsWith('/auth/') || request.path.startsWith('/user/')
      ? await auth.handle(request, db)
      : request.path.startsWith('/order') || /^\/showtime\/\d+\/seats$/.test(request.path)
        ? await orders.handle(request, db)
      : request.path.startsWith('/coupon/') || request.path.startsWith('/admin/coupon-codes')
        ? await coupons.handle(request, db)
      : request.path.startsWith('/comment/') || request.path === '/comment'
        ? await comments.handle(request, db)
      : await catalog.handle(request, db)
    return result?.mpserverlessComposedResponse ? result : core.response(result, origin)
  } catch (error) {
    return core.response(core.failure(error), request?.headers.origin || '')
  }
}
