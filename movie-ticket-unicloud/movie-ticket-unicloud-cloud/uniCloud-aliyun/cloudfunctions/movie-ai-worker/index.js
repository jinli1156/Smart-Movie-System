'use strict'
exports.main=async(event,context)=>{
  // Cloud trigger removal is a separate console operation. Timers never scan jobs.
  if(context?.SOURCE==='timing')return {disabled:true,reason:'demand-only'}
  if(!['function','server'].includes(context?.SOURCE)||event?.action!=='advance')throw new Error('Internal demand invocation only')
  const filter=event.filter
  if(!filter||typeof filter!=='object'||Array.isArray(filter)||Object.keys(filter).some(key=>!['ownerId','kind','campaignId','batchId','id'].includes(key)))throw new Error('Invalid scope')
  if(!Number.isSafeInteger(filter.ownerId)&&!(typeof filter.campaignId==='string'&&filter.campaignId.length<=100))throw new Error('Scope required')
  for(const [key,value]of Object.entries(filter))if(key!=='ownerId'&&(typeof value!=='string'||value.length>100))throw new Error('Invalid scope value')
  await require('./runtime/ai-demand-runtime').advance(uniCloud.database(),filter)
  return {processed:true,mode:'demand'}
}
