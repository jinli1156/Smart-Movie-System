'use strict'
const ai=require('./ai-common')
const enabled=()=>ai.config().executionMode==='demand'
async function advance(db,filter){
  if(!enabled())return
  const rows=(await db.collection('mts_ai_job').where({...filter,status:db.command.in(['QUEUED','GENERATING','RUNNING','PLANNING','SUBMITTING','PERSISTING'])}).orderBy('createdAt','asc').limit(200).get()).data,now=Date.now()
  const active=rows.some(job=>job.status==='QUEUED'||job.status==='GENERATING'&&job.leaseUntil<=now&&now-(job.providerPolledAt||0)>=8000||['RUNNING','PLANNING','SUBMITTING','PERSISTING'].includes(job.status)&&job.leaseUntil<now)
  if(!active)return
  const response=await uniCloud.callFunction({name:'movie-ai-worker',data:{action:'advance',filter}})
  if(response.result?.error)throw new Error('任务处理暂时不可用，请稍后刷新')
}
module.exports={enabled,advance}
