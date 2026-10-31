'use strict'
// No timer, detached promise, self invocation, or browser access to provider keys.
const crypto=require('crypto'),ai=require('./ai-common'),worker=require('./ai-worker')
const enabled=()=>ai.config().executionMode==='demand'
async function advance(db,filter,{limit=2}={}){
  if(!enabled())return
  const rows=(await db.collection('mts_ai_job').where({...filter,status:db.command.in(['QUEUED','GENERATING','RUNNING','PLANNING','SUBMITTING','PERSISTING'])}).orderBy('createdAt','asc').limit(200).get()).data
  const now=Date.now(),candidates=[]
  for(const row of rows){
    if(['RUNNING','PLANNING','SUBMITTING','PERSISTING'].includes(row.status)&&row.leaseUntil<now){
      const status=row.status==='SUBMITTING'&&!row.providerTask?'SUBMIT_UNKNOWN':row.providerResult||row.providerTask?'QUEUED':'FAILED'
      await db.collection('mts_ai_job').where({_id:row.id,status:row.status,leaseToken:row.leaseToken}).update({status,leaseUntil:0,leaseToken:'',updatedAt:now,errorMessage:status==='QUEUED'?null:'任务曾中断，请核实后处理'})
      // Do not act on a stale row; the next request observes the recovery result.
      continue
    }
    if(row.leaseUntil>now)continue
    if(row.status==='GENERATING'&&now-(row.providerPolledAt||0)>=8000)candidates.push(row)
    else if(row.status==='QUEUED')candidates.push(row)
  }
  // Finish submitted work before starting more work. At most two model operations per request.
  candidates.sort((a,b)=>Number(b.status==='GENERATING')-Number(a.status==='GENERATING'))
  await Promise.all(candidates.slice(0,limit).map(async row=>{
    const leaseToken=crypto.randomUUID(),leaseUntil=Date.now()+125000
    const claimed=await db.collection('mts_ai_job').where({_id:row.id,status:row.status,leaseToken:row.leaseToken,leaseUntil:row.leaseUntil}).update({status:'RUNNING',leaseToken,leaseUntil,updatedAt:Date.now()})
    if(claimed.updated===1)await worker.work(db,{...row,status:'RUNNING',leaseToken,leaseUntil},{asynchronous:true})
  }))
}
module.exports={enabled,advance}
