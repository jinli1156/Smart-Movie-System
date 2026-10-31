'use strict'
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('fs'),path=require('path')
const database=require('./helpers/ai-db')
const runtime=path.resolve(__dirname,'../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-ai-worker/runtime')
const built=fs.existsSync(path.join(runtime,'ai-worker.js'))
const run=(name,fn)=>test(name,{skip:!built&&'Prepare the AI worker runtime before release validation'},fn)
function fixture(){return database({mts_user:[{_id:'u2',id:2,role:'USER',status:1,deleted:0}],mts_ai_job:[{_id:'job',id:'job',ownerId:2,kind:'POSTER',input:{movieTitle:'科幻电影',prompt:'合成主角',style:'SCIFI',ratio:'1:1'},status:'QUEUED',leaseToken:'',leaseUntil:0,providerCalls:0,attempts:0,createdAt:Date.now(),updatedAt:Date.now()}]})}
async function mock(t,failResult=false){
  const oldCloud=global.uniCloud,oldFetch=global.fetch,files=new Map(),metrics={calls:0,uploads:0}
  t.after(()=>{global.uniCloud=oldCloud;global.fetch=oldFetch})
  const PNG=require(path.join(runtime,'../node_modules/pngjs')).PNG,png=new PNG({width:64,height:64});png.data.fill(90)
  const bytes=PNG.sync.write(png)
  global.uniCloud={
    async uploadFile({fileContent}){metrics.uploads++;if(failResult&&metrics.uploads===2)throw new Error('Storage interruption');const fileID='file-'+metrics.uploads;files.set(fileID,fileContent);return{fileID}},
    async downloadFile({fileID}){assert(files.has(fileID));return{fileContent:files.get(fileID)}},
    async deleteFile({fileList}){fileList.forEach(id=>files.delete(id));return{}}
  }
  global.fetch=async()=>{metrics.calls++;return new Response(JSON.stringify({data:[{b64_json:bytes.toString('base64')}]}),{headers:{'Content-Type':'application/json'}})}
  return{files,metrics,PNG}
}
run('packaged worker stores an encrypted PNG and completes exactly one paid call under overlapping ticks',async t=>{
  const {metrics,PNG}=await mock(t),worker=require(path.join(runtime,'ai-worker')),ai=require(path.join(runtime,'ai-common')),db=fixture()
  await Promise.all([worker.tick(db),worker.tick(db)])
  const job=db.tables.mts_ai_job[0];assert.equal(job.status,'SUCCEEDED');assert.equal(job.providerCalls,1);assert.equal(metrics.calls,1)
  const decoded=PNG.sync.read(await ai.readPrivate(job.storageKey));assert.equal(decoded.width,64)
})
run('packaged worker resumes a stored result after storage interruption without another model call',async t=>{
  const {metrics}=await mock(t,true),worker=require(path.join(runtime,'ai-worker')),ai=require(path.join(runtime,'ai-common')),db=fixture()
  await worker.tick(db);let job=db.tables.mts_ai_job[0];assert.equal(job.status,'FAILED');assert(job.providerResult)
  await ai.retry(db,job);await worker.tick(db);job=db.tables.mts_ai_job[0]
  assert.equal(job.status,'SUCCEEDED');assert.equal(metrics.calls,1);assert.equal(job.providerCalls,1)
})
run('packaged worker marks an interrupted paid submission uncertain and does not replay it',async t=>{
  const {metrics}=await mock(t),worker=require(path.join(runtime,'ai-worker')),db=fixture()
  global.fetch=async()=>{metrics.calls++;throw new Error('Connection interrupted')}
  await worker.tick(db);assert.equal(db.tables.mts_ai_job[0].status,'SUBMIT_UNKNOWN')
  await worker.tick(db);assert.equal(metrics.calls,1)
})
