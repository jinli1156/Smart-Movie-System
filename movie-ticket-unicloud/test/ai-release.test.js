'use strict'
process.env.AI_EXECUTION_MODE='legacy-test'
const test=require('node:test'),assert=require('node:assert/strict'),crypto=require('crypto')
const base='../movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions/movie-api/'
const ai=require(base+'ai-common'),state=require(base+'ai-state'),listing=require(base+'listing'),source=require(base+'listing-source'),marketing=require(base+'ai-marketing'),personal=require(base+'ai-personal'),worker=require(base+'ai-worker')
const database=require('./helpers/ai-db')
const admin={id:1,role:'ADMIN'},buyer={id:2,role:'USER'},manager={id:3,role:'CINEMA_ADMIN',cinemaId:10}
const req=(path,user=admin,method='GET',body={},query={})=>({path,user,method,body,query,headers:{}})
const fixture=()=>database({mts_user:[admin,buyer,manager].map(u=>({_id:'u'+u.id,...u,status:1,deleted:0})),mts_cinema:[{_id:'c10',id:10,name:'影院',deleted:0}],mts_movie:[{_id:'m1',id:1,title:'真实电影',description:'真实剧情简介',genre:'科幻',duration:120,status:1,deleted:0}],mts_counter:[{_id:'movie',seq:50}]})
test('cloud JSON upload preserves Unicode and binary bytes and rejects truncated base64',async()=>{
  const bytes=Buffer.from([137,80,78,71,0,255,128]),r={headers:{'content-type':'application/json'},body:{fields:{text:'中文资料'},files:[{name:'photo',filename:'人像.png',mime:'image/png',base64:bytes.toString('base64')}]}}
  const parsed=await ai.multipart(r);assert.equal(parsed.fields.text,'中文资料');assert.deepEqual(parsed.files[0].bytes,bytes)
  r.body.files[0].base64+='x';await assert.rejects(ai.multipart(r),e=>[400,413].includes(e.code))
})
test('Aliyun encrypted files use a temporary storage URL and authenticated decryption',async t=>{
  const oldCloud=global.uniCloud,oldFetch=global.fetch,oldKey=process.env.AI_ASSET_KEY
  t.after(()=>{global.uniCloud=oldCloud;global.fetch=oldFetch;if(oldKey===undefined)delete process.env.AI_ASSET_KEY;else process.env.AI_ASSET_KEY=oldKey})
  const key=crypto.randomBytes(32).toString('hex');process.env.AI_ASSET_KEY=key
  const plain=Buffer.from('private result'),encrypted=state.encrypt(plain,key)
  global.uniCloud={async getTempFileURL(){return{fileList:[{tempFileURL:'https://storage.next.bspapp.com/private.enc'}]}}}
  global.fetch=async url=>{assert.equal(url.hostname,'storage.next.bspapp.com');return new Response(encrypted)}
  assert.deepEqual(await ai.readPrivate('server-file-id'),plain)
})
test('private file manifests keep gateway responses bounded and reconstruct the exact original',()=>{
  const input=crypto.randomBytes(1800000),manifest=ai.fileResponse(input).data
  assert(manifest.parts>1);assert.equal(manifest.size,input.length)
  const chunks=Array.from({length:manifest.parts},(_,part)=>{
    const response=ai.fileResponse(input,{part:String(part)}),bytes=Buffer.from(response.body,'base64')
    assert(bytes.length<=512*1024);return bytes
  })
  assert.deepEqual(Buffer.concat(chunks),input)
  assert.throws(()=>ai.fileResponse(input,{part:-1}),e=>e.code===400)
})
test('AI quota and replay reserve once, reject changed content, and conflict concurrent snapshots',async()=>{
  const db=fixture(),input={ownerId:2,kind:'POSTER',requestId:'r1',input:{title:'hello'},dailyLimit:1}
  const j=await state.enqueue(db,input);assert.equal((await state.enqueue(db,input)).id,j.id)
  assert.equal(db.tables.mts_ai_quota[0].used,1)
  await assert.rejects(state.enqueue(db,{...input,input:{title:'changed'}}),e=>e.code===409)
  await assert.rejects(state.enqueue(db,{...input,requestId:'r2'}),e=>e.code===429)
  const concurrent=fixture(),attempts=await Promise.allSettled([state.enqueue(concurrent,input),state.enqueue(concurrent,{...input,requestId:'r2'})])
  assert.equal(attempts.filter(r=>r.status==='fulfilled').length,1);assert.equal(concurrent.tables.mts_ai_quota[0].used,1)
})
test('private image file route does not disclose another user task',async()=>{
  const db=fixture(),j=await state.enqueue(db,{ownerId:2,kind:'POSTER',requestId:'private',input:{},dailyLimit:5})
  await assert.rejects(personal.handle(req('/posters/'+j.id+'/file',{id:9,role:'USER'}),db),e=>e.code===404)
  assert(!('input' in ai.taskView(j)));assert(!('portraitKey'in ai.taskView(j)))
})
test('listing validates allowlisted fields, dates, seat layout and strips injected owner/role',()=>{
  const fields=listing.clean('MOVIE',{title:'电影',genre:'科幻',description:'剧情',duration:120,status:0,ownerId:99,role:'ADMIN'})
  assert.equal(fields.ownerId,undefined);assert.deepEqual(listing.issues('MOVIE',fields),[])
  assert(listing.issues('MOVIE',{...fields,releaseDate:'2026-02-30'}).length)
  assert(listing.issues('HALL',{name:'厅',totalRows:2,totalCols:2,seatLayout:'[[1,1]]',status:1}).length)
  assert(listing.issues('MOVIE',{...fields,poster:'javascript:alert(1)'}).length)
})
test('CSV quoted multiline and XLSX templates round-trip without executing formulas',async()=>{
  const csv='title,description,duration,genre,status\r\n"片名","第一行\n第二行",120,剧情,0'
  const rows=source.records(csv,listing.schemas.MOVIE);assert.equal(rows[0].description,'第一行\n第二行')
  const template=source.template('MOVIE',listing.schemas.MOVIE,'xlsx')
  const text=await source.read({filename:'movie.xlsx',bytes:Buffer.from(template.body,'base64')})
  assert(text.includes('title'));assert.equal(source.records(text,listing.schemas.MOVIE)[0].status,'0')
})
test('listing publication checks cinema scope, optimistic version and does not duplicate entities on replay',async()=>{
  const db=fixture(),fields={title:'新片',genre:'科幻',duration:120,description:'简介',status:0},id='draft1'
  await db.collection('mts_listing_draft').add({_id:id,id,kind:'MOVIE',ownerId:1,status:'DRAFT',version:1,fields,history:[],updatedAt:new Date().toISOString()})
  await assert.rejects(listing.handle(req('/listing/drafts/'+id+'/publish',manager,'POST',{version:1,acknowledged:true}),db),e=>e.code===403)
  await assert.rejects(listing.handle(req('/listing/drafts/'+id+'/publish',admin,'POST',{version:0,acknowledged:true}),db),e=>e.code===409)
  const result=await listing.handle(req('/listing/drafts/'+id+'/publish',admin,'POST',{version:1,acknowledged:true}),db)
  assert.equal(result.data.entityId,51)
  await listing.handle(req('/listing/drafts/'+id+'/publish',admin,'POST',{version:1,acknowledged:true}),db)
  assert.equal(db.tables.mts_movie.length,2)
})
test('marketing batch idempotency reserves quota atomically and rejects changed poster content',async()=>{
  const db=fixture(),id='campaign',input={kind:'IMAGE',requestKey:'batch1',imageCount:2,posterContent:{movieId:1,title:'标题',copy:'文案'}}
  await db.collection('mts_marketing').add({_id:id,id,type:'CAMPAIGN',cinemaId:10,movieSnapshot:[{id:1,title:'电影'}],styles:['自由创作'],ratios:['3:4'],brief:'需求',audience:'观众',cinemaName:'影院'})
  const first=await marketing.generate(req('',admin,'POST',input),db,id)
  const second=await marketing.generate(req('',admin,'POST',input),db,id)
  assert.equal(first.id,second.id);assert.equal(db.tables.mts_ai_job.length,2);assert.equal(db.tables.mts_ai_quota[0].used,2)
  await assert.rejects(marketing.generate(req('',admin,'POST',{...input,imageCount:3}),db,id),e=>e.code===409)
})
test('marketing requires administrator review, immutable versions, scoped assets and idempotent publication',async()=>{
  const db=fixture(),c={_id:'c',id:'c',type:'CAMPAIGN',cinemaId:10,cinemaName:'影院',movieSnapshot:[{id:1,title:'电影'}]}
  await db.collection('mts_marketing').add(c)
  await db.collection('mts_marketing').add({_id:'a',id:'a',type:'ASSET',campaignId:'c',cinemaId:10,movieId:1})
  const v=(await marketing.handle(req('/admin/marketing/campaigns/c/versions',manager,'POST',{movieId:1,title:'标题',copy:'文案',assetId:'a'}),db)).data
  await assert.rejects(marketing.handle(req('/admin/marketing/versions/'+v.id+'/review',manager,'POST',{approve:true}),db),e=>e.code===403)
  const p={slot:'HOME',endAt:'2099-01-01T20:00:00',idempotencyKey:'publish1'}
  await assert.rejects(marketing.handle(req('/admin/marketing/versions/'+v.id+'/publish',admin,'POST',p),db),e=>e.code===409)
  await marketing.handle(req('/admin/marketing/versions/'+v.id+'/submit-review',manager,'POST'),db)
  await marketing.handle(req('/admin/marketing/versions/'+v.id+'/review',admin,'POST',{approve:true}),db)
  const pub=await marketing.handle(req('/admin/marketing/versions/'+v.id+'/publish',admin,'POST',p),db)
  await marketing.handle(req('/admin/marketing/versions/'+v.id+'/publish',admin,'POST',p),db)
  assert.equal(db.tables.mts_marketing.filter(r=>r.type==='PUBLICATION').length,1)
  assert.equal((await marketing.placements(req('',null,'GET',{}, {slot:'HOME'}),db)).length,1)
  await marketing.handle(req('/admin/marketing/publications/'+pub.data.id+'/offline',admin,'POST'),db)
  assert.equal((await marketing.placements(req('',null,'GET',{}, {slot:'HOME'}),db)).length,0)
})
test('uncertain jobs never automatically submit again; expired worker leases recover deterministically',async()=>{
  const db=fixture(),j=await state.enqueue(db,{ownerId:2,kind:'POSTER',requestId:'expired',input:{},dailyLimit:5})
  await db.collection('mts_ai_job').doc(j.id).update({status:'SUBMITTING',providerCalls:1,leaseToken:'old',leaseUntil:1})
  await worker.tick(db);assert.equal(db.tables.mts_ai_job[0].status,'SUBMIT_UNKNOWN')
  await assert.rejects(ai.retry(db,db.tables.mts_ai_job[0]),e=>e.code===409)
  assert.throws(()=>worker.allowedUrl('https://127.0.0.1/private'),e=>e.code===502)
  assert.throws(()=>worker.allowedUrl('http://dashscope-result.oss-cn-beijing.aliyuncs.com/x'),e=>e.code===502)
})
test('souvenir rejects unpaid, refunded and foreign orders before any task or quota write',async()=>{
  const db=fixture();await db.collection('mts_order').add({_id:'o',id:1,orderNo:'order',userId:2,status:0})
  await assert.rejects(personal.createSouvenir(req('',buyer,'POST',{note:'心情'}),db,buyer,'order'),e=>e.code===409)
  await assert.rejects(personal.createSouvenir(req('',admin,'POST',{}),db,admin,'order'),e=>e.code===404)
  assert.equal((db.tables.mts_ai_job||[]).length,0)
})
