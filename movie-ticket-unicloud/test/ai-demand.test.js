'use strict'
const test=require('node:test'),assert=require('node:assert/strict'),path=require('path')
const database=require('./helpers/ai-db')
const root=path.resolve(__dirname,'../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions')
const runtime=path.join(root,'movie-ai-worker/runtime'),demand=require(path.join(runtime,'ai-demand-runtime')),ai=require(path.join(runtime,'ai-common'))
const apiDemand=require(path.join(root,'movie-api/ai-demand')),entry=require(path.join(root,'movie-ai-worker'))
function fixture(){return database({mts_user:[{_id:'u2',id:2,role:'USER',status:1,deleted:0}],mts_ai_job:[{_id:'job',id:'job',ownerId:2,kind:'POSTER',input:{movieTitle:'电影',prompt:'星空',style:'SCIFI',ratio:'1:1'},status:'QUEUED',leaseToken:'',leaseUntil:0,providerCalls:0,attempts:0,createdAt:1,updatedAt:1}]})}
function setup(t){
  const oldCloud=global.uniCloud,oldFetch=global.fetch,keys=['AI_EXECUTION_MODE','AI_IMAGE_PROVIDER','AI_IMAGE_MODEL','AI_IMAGE_KEY','AI_TEXT_IMAGE_KEY','AI_TEXT_IMAGE_MODEL','AI_ASSET_KEY'],old=Object.fromEntries(keys.map(k=>[k,process.env[k]]))
  Object.assign(process.env,{AI_EXECUTION_MODE:'demand',AI_IMAGE_PROVIDER:'qwen-images',AI_IMAGE_MODEL:'qwen-image-3.0',AI_IMAGE_KEY:'qwen-test-key',AI_TEXT_IMAGE_KEY:'zhipu-test-key',AI_TEXT_IMAGE_MODEL:'glm-image',AI_ASSET_KEY:'ab'.repeat(32)})
  t.after(()=>{global.uniCloud=oldCloud;global.fetch=oldFetch;for(const k of keys){if(old[k]===undefined)delete process.env[k];else process.env[k]=old[k]}})
  const PNG=require(path.join(root,'movie-ai-worker/node_modules/pngjs')).PNG,png=new PNG({width:64,height:64});png.data.fill(80)
  const bytes=PNG.sync.write(png),files=new Map(),metrics={submit:0,qwenSubmit:0,zhipuSubmit:0,poll:0,download:0,lastPrompt:''},mock={status:'RUNNING',failPoll:false,loseSubmission:false,failStorage:false,rejectZhipu:false}
  global.uniCloud={async uploadFile({fileContent}){if(mock.failStorage)throw new Error('storage unavailable');const fileID='f'+files.size;files.set(fileID,fileContent);return{fileID}},async downloadFile({fileID}){return{fileContent:files.get(fileID)}},async deleteFile(){}}
  global.fetch=async(url,options={})=>{
    url=String(url)
    if(url.endsWith('/services/aigc/image-generation/generation')){
      metrics.submit++;metrics.qwenSubmit++;assert.equal(options.headers.Authorization,'Bearer qwen-test-key');assert.equal(options.headers['X-DashScope-Async'],'enable');assert.equal(JSON.parse(options.body).model,'qwen-image-3.0')
      assert(JSON.parse(options.body).input.messages[0].content.some(c=>c.image?.startsWith('data:image/png;base64,')))
      if(mock.loseSubmission)throw new Error('submission connection lost')
      return Response.json({output:{task_id:'task-123',task_status:'PENDING'}})
    }
    if(url.endsWith('/async/images/generations')){
      metrics.submit++;metrics.zhipuSubmit++;const body=JSON.parse(options.body)
      metrics.lastPrompt=body.prompt
      assert.equal(options.headers.Authorization,'Bearer zhipu-test-key');assert.equal(body.model,'glm-image');assert(body.prompt.length<=1000);assert(!('input' in body));assert(['1280x1280','1152x1536','2048x1152'].includes(body.size))
      if(mock.loseSubmission)throw new Error('submission connection lost')
      if(mock.rejectZhipu)return Response.json({error:{code:'1302'}},{status:429})
      return Response.json({id:'task-123',model:'glm-image',task_status:'PROCESSING'})
    }
    if(url.endsWith('/tasks/task-123')||url.endsWith('/async-result/task-123')){
      metrics.poll++;if(mock.failPoll)throw new Error('progress connection lost')
      if(url.includes('/async-result/')){assert.equal(options.headers.Authorization,'Bearer zhipu-test-key');return Response.json({task_status:mock.status==='RUNNING'?'PROCESSING':mock.status==='SUCCEEDED'?'SUCCESS':mock.status,image_result:[{url:'https://aigc-files.bigmodel.cn/result.png'}]})}
      assert.equal(options.headers.Authorization,'Bearer qwen-test-key')
      return Response.json({output:{task_status:mock.status,choices:[{message:{content:[{image:'https://dashscope-result-sh.oss-cn-shanghai.aliyuncs.com/result.png'}]}}]}})
    }
    assert(url.endsWith('/result.png'));metrics.download++;return new Response(bytes)
  }
  return{metrics,mock,PNG,bytes}
}
const due=async db=>db.collection('mts_ai_job').doc('job').update({providerPolledAt:0})
test('a reference photo alone routes to Qwen, and model IDs and credentials stay separated',async t=>{
  const {metrics,mock,bytes}=setup(t),db=fixture(),portraitKey=await ai.putPrivate(bytes,'portrait')
  await db.collection('mts_ai_job').doc('job').update({portraitKey,input:{...db.tables.mts_ai_job[0].input,photoHash:ai.hash(bytes.toString('base64'))}})
  await demand.advance(db,{ownerId:2});let job=db.tables.mts_ai_job[0]
  assert.equal(job.status,'GENERATING');assert.equal(job.imageModel,'qwen-image-3.0');assert.equal(metrics.qwenSubmit,1);assert.equal(metrics.zhipuSubmit,0)
  mock.status='SUCCEEDED';await due(db);await demand.advance(db,{ownerId:2});job=db.tables.mts_ai_job[0]
  assert.equal(job.status,'SUCCEEDED');assert.equal(metrics.submit,1)
})
test('old Qwen tasks without provider metadata still query their original provider without a new generation',async t=>{
  const {metrics,mock}=setup(t),db=fixture();mock.status='SUCCEEDED'
  await db.collection('mts_ai_job').doc('job').update({status:'GENERATING',providerCalls:1,providerPolledAt:0,providerTask:{taskId:'task-123',base:ai.config().base,submittedAt:Date.now()}})
  await demand.advance(db,{ownerId:2});assert.equal(db.tables.mts_ai_job[0].status,'SUCCEEDED');assert.equal(metrics.submit,0);assert.equal(metrics.poll,1)
})
test('marketing images use GLM, preserve final marketing text, and save the actual model in asset metadata',async t=>{
  const {metrics,mock}=setup(t),db=fixture();await db.collection('mts_user').doc('u2').update({role:'ADMIN'})
  await db.collection('mts_marketing').add({_id:'campaign',id:'campaign',cinemaId:10})
  await db.collection('mts_ai_job').doc('job').update({kind:'MARKETING_IMAGE',campaignId:'campaign',cinemaId:10,plan:{imagePrompt:'电影构图。'.repeat(250)},input:{movie:{id:1,title:'电影'},posterContent:{movieId:1,title:'真实营销标题',copy:'真实短文案'},cinemaName:'测试影院',style:'自由创作',ratio:'16:9'}})
  await demand.advance(db,{campaignId:'campaign'});assert.equal(metrics.zhipuSubmit,1);assert.equal(metrics.qwenSubmit,0)
  assert(metrics.lastPrompt.includes('真实营销标题'));assert(metrics.lastPrompt.includes('真实短文案'))
  mock.status='SUCCEEDED';await due(db);await demand.advance(db,{campaignId:'campaign'})
  const asset=db.tables.mts_marketing.find(x=>x.type==='ASSET');assert.equal(asset.model,'glm-image');assert.equal(db.tables.mts_ai_job[0].status,'SUCCEEDED')
  assert.equal(ai.imageSettings({kind:'SOUVENIR',input:{}}).model,'glm-image')
})
test('a Zhipu rejection does not fall back to Qwen or silently start another paid generation',async t=>{
  const {metrics,mock}=setup(t),db=fixture();mock.rejectZhipu=true
  await demand.advance(db,{ownerId:2});await demand.advance(db,{ownerId:2})
  assert.equal(db.tables.mts_ai_job[0].status,'FAILED');assert.equal(metrics.zhipuSubmit,1);assert.equal(metrics.qwenSubmit,0)
})
test('on-demand overlapping submissions reserve once, persist task ID, and query without resubmission',async t=>{
  const {metrics,mock,PNG}=setup(t),db=fixture()
  await Promise.all([demand.advance(db,{ownerId:2}),demand.advance(db,{ownerId:2})])
  let job=db.tables.mts_ai_job[0];assert.equal(job.status,'GENERATING');assert.equal(job.providerTask.taskId,'task-123');assert.equal(metrics.submit,1)
  assert.equal(job.providerTask.provider,'zhipu');assert.equal(job.imageModel,'glm-image');assert.equal(metrics.qwenSubmit,0)
  await demand.advance(db,{ownerId:2});assert.equal(metrics.poll,0)
  await due(db);await demand.advance(db,{ownerId:2});assert.equal(metrics.poll,1);assert.equal(db.tables.mts_ai_job[0].status,'GENERATING')
  mock.status='SUCCEEDED';await due(db);await Promise.all([demand.advance(db,{ownerId:2}),demand.advance(db,{ownerId:2})])
  job=db.tables.mts_ai_job[0];assert.equal(job.status,'SUCCEEDED');assert.equal(metrics.submit,1);assert.equal(metrics.download,1)
  assert.equal(PNG.sync.read(await ai.readPrivate(job.storageKey)).width,64)
})
test('read-only provider progress failures preserve task ID and never authorize another image request',async t=>{
  const {metrics,mock}=setup(t),db=fixture();await demand.advance(db,{ownerId:2});mock.failPoll=true
  await due(db);await demand.advance(db,{ownerId:2});let job=db.tables.mts_ai_job[0]
  assert.equal(job.status,'GENERATING');assert.equal(job.providerTask.taskId,'task-123');assert.equal(metrics.submit,1)
  assert.equal((await ai.retry(db,job)).status,'GENERATING')
  mock.failPoll=false;mock.status='UNKNOWN';await due(db);await demand.advance(db,{ownerId:2});job=db.tables.mts_ai_job[0]
  assert.equal(job.status,'FAILED');await assert.rejects(ai.retry(db,job),e=>e.code===409);assert.equal(metrics.submit,1)
})
test('uncertain async submission and expired submitting leases never automatically repeat a paid call',async t=>{
  const {metrics,mock}=setup(t),db=fixture();mock.loseSubmission=true
  await demand.advance(db,{ownerId:2});assert.equal(db.tables.mts_ai_job[0].status,'SUBMIT_UNKNOWN')
  await demand.advance(db,{ownerId:2});assert.equal(metrics.submit,1)
  const expired=fixture();await expired.collection('mts_ai_job').doc('job').update({status:'SUBMITTING',providerCalls:1,leaseUntil:1})
  await demand.advance(expired,{ownerId:2});assert.equal(expired.tables.mts_ai_job[0].status,'SUBMIT_UNKNOWN');assert.equal(metrics.submit,1)
})
test('saving a completed provider task can resume without creating another provider task',async t=>{
  const {metrics,mock}=setup(t),db=fixture();await demand.advance(db,{ownerId:2});mock.status='SUCCEEDED';mock.failStorage=true
  await due(db);await demand.advance(db,{ownerId:2});let job=db.tables.mts_ai_job[0]
  assert.equal(job.status,'FAILED');assert(job.providerResult)
  mock.failStorage=false;await ai.retry(db,job);await demand.advance(db,{ownerId:2});job=db.tables.mts_ai_job[0]
  assert.equal(job.status,'SUCCEEDED');assert.equal(metrics.submit,1)
})
test('idle pages do not invoke the worker; another owner cannot advance pending work',async t=>{
  setup(t);const db=fixture();let calls=0;global.uniCloud.callFunction=async()=>{calls++;return{result:{processed:true}}}
  await apiDemand.advance(db,{ownerId:99});assert.equal(calls,0)
  await demand.advance(db,{ownerId:99});assert.equal(db.tables.mts_ai_job[0].status,'QUEUED')
  await db.collection('mts_ai_job').doc('job').update({status:'SUCCEEDED'})
  await apiDemand.advance(db,{ownerId:2});assert.equal(calls,0)
})
test('new queued jobs are not hidden by more than 200 historical terminal jobs',async t=>{
  setup(t);const db=fixture();for(let i=0;i<205;i++)await db.collection('mts_ai_job').add({_id:'old'+i,id:'old'+i,ownerId:2,status:'SUCCEEDED',createdAt:-1000+i})
  let calls=0;global.uniCloud.callFunction=async()=>{calls++;return{result:{processed:true}}}
  await apiDemand.advance(db,{ownerId:2});assert.equal(calls,1)
})
test('worker rejects public sources and timer invocations do no database or model work',async t=>{
  setup(t);global.uniCloud.database=()=>{throw new Error('must not access database')}
  assert.deepEqual(await entry.main({}, {SOURCE:'timing'}),{disabled:true,reason:'demand-only'})
  for(const SOURCE of ['client','http'])await assert.rejects(entry.main({action:'advance',filter:{ownerId:2}},{SOURCE}))
  await assert.rejects(entry.main({action:'advance',filter:{ownerId:{$ne:0}}},{SOURCE:'function'}))
})
test('default CLI invocation checks internal wiring without querying a model or changing business data',async t=>{
  setup(t);let calls=0
  global.fetch=async()=>{throw new Error('CLI check must not invoke a model')}
  global.uniCloud.callFunction=async ({name,data})=>{calls++;assert.equal(name,'movie-ai-worker');assert.deepEqual(data,{action:'advance',filter:{ownerId:-1,kind:'POSTER'}});return{result:{processed:true,mode:'demand'}}}
  const api=require(path.join(root,'movie-api/index'))
  assert.deepEqual(await api.main({}, {SOURCE:'server'}),{mode:'demand',worker:{processed:true,mode:'demand'}})
  assert.equal(calls,1)
})
