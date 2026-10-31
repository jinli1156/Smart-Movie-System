'use strict'
const crypto=require('crypto')
const {assert,ApiError,success,requireUser,numericId,now}=require('./core')
const {requireCinema,scopeCinemaId}=require('./permissions')
const ai=require('./ai-common'),state=require('./ai-state')
const demand=require('./ai-demand')
const TABLE='mts_marketing'
async function campaign(request,db,id){const c=await ai.get(db,TABLE,id);assert(c?.type==='CAMPAIGN',404,'营销需求不存在');requireCinema(request,c.cinemaId);return c}
const baseView=c=>{const {_id,type,...out}=c;return out}
async function detail(request,db,id){
  const c=await campaign(request,db,id)
  await demand.advance(db,{campaignId:id})
  const [rows,jobs]=await Promise.all([db.collection(TABLE).where({campaignId:id}).limit(1000).get(),db.collection('mts_ai_job').where({campaignId:id}).orderBy('createdAt','desc').limit(200).get()])
  return {...baseView(c),tasks:jobs.data.map(j=>({id:j.id,batchId:j.batchId,movieId:j.input.movie.id,kind:j.kind==='MARKETING_COPY'?'COPY':'IMAGE',style:j.input.style,ratio:j.input.ratio,status:j.status,attempts:j.attempts,providerCalls:j.providerCalls,errorMessage:j.errorMessage,result:j.result,model:j.imageModel||j.providerTask?.model||(j.providerTask?'qwen-image-3.0':j.providerCalls?'历史模型':ai.config().textImageModel)})),
    assets:rows.data.filter(r=>r.type==='ASSET').map(r=>({...baseView(r),previewUrl:'/api/admin/marketing/assets/'+r.id+'/file'})),
    versions:rows.data.filter(r=>r.type==='VERSION').sort((a,b)=>b.version-a.version).map(baseView),publications:rows.data.filter(r=>r.type==='PUBLICATION').map(baseView),logs:rows.data.filter(r=>r.type==='LOG').map(baseView)}
}
async function log(db,c,operatorId,action,reason=''){const id=crypto.randomUUID();await db.collection(TABLE).add({_id:id,id,type:'LOG',campaignId:c.id,cinemaId:c.cinemaId,operatorId,action,reason,createdAt:now()})}
async function create(request,db){
  const b=request.body,cinemaId=numericId(b.cinemaId);requireCinema(request,cinemaId)
  const cinema=(await db.collection('mts_cinema').where({id:cinemaId,deleted:0}).limit(1).get()).data?.[0];assert(cinema,400,'影院不存在')
  assert(Array.isArray(b.movieIds)&&b.movieIds.length>=1&&b.movieIds.length<=10&&new Set(b.movieIds).size===b.movieIds.length,400,'请选择1–10部电影')
  assert(Array.isArray(b.styles)&&b.styles.length>=1&&b.styles.length<=2&&b.styles.every(s=>['自由创作','简洁','节日'].includes(s)),400,'风格无效')
  assert(Array.isArray(b.ratios)&&b.ratios.length>=1&&b.ratios.length<=3&&b.ratios.every(r=>ai.ratios.includes(r)),400,'画幅无效')
  const movieSnapshot=[]
  for(const id of b.movieIds){const m=(await db.collection('mts_movie').where({id:numericId(id),deleted:0}).limit(1).get()).data?.[0];assert(m&&[1,2].includes(m.status),400,'影片不存在或已下架');movieSnapshot.push({id:m.id,title:m.title,genre:m.genre||'',description:m.description||''})}
  const id=crypto.randomUUID(),c={_id:id,id,type:'CAMPAIGN',ownerId:request.user.id,cinemaId,cinemaName:cinema.name,name:ai.text(b.name,120,'需求名称',true),brief:ai.text(b.brief,600,'创作需求',true),audience:ai.text(b.audience,80,'目标受众',true),movieSnapshot,styles:[...new Set(b.styles)],ratios:[...new Set(b.ratios)],createdAt:now()}
  await db.collection(TABLE).add(c);return detail(request,db,id)
}
async function generate(request,db,cid){
  const c=await campaign(request,db,cid),b=request.body,cfg=ai.config(),requestKey=ai.text(b.requestKey,80,'请求标识',true)
  assert(['COPY','IMAGE'].includes(b.kind),400,'生成类型无效');assert(cfg.textConfigured&&(b.kind!=='IMAGE'||cfg.imageConfigured),503,'AI生成服务未配置')
  let inputs=[]
  if(b.kind==='COPY')inputs=c.movieSnapshot.map(movie=>({movie,brief:c.brief,audience:c.audience,cinemaName:c.cinemaName}))
  else{
    assert(Number.isInteger(b.imageCount)&&b.imageCount>=1&&b.imageCount<=cfg.batchLimit,400,'每批生成1–18张海报')
    const p=b.posterContent,movie=c.movieSnapshot.find(m=>m.id===p?.movieId);assert(movie,400,'海报影片不属于当前需求')
    const content={movieId:movie.id,title:ai.text(p.title,60,'营销标题',true),copy:ai.text(p.copy,300,'营销文案',true)}
    inputs=Array.from({length:b.imageCount},(_,i)=>({movie,brief:c.brief,audience:c.audience,cinemaName:c.cinemaName,posterContent:content,style:c.styles[i%c.styles.length],ratio:c.ratios[i%c.ratios.length]}))
  }
  // Reserve every task and the total quota in one transaction. A partial batch
  // must never consume quota without returning a durable, replayable batch.
  const id=ai.hash(c.id,b.kind,requestKey),inputHash=ai.hash(inputs),tx=await db.startTransaction()
  try{
    const existing=ai.object(await tx.collection(TABLE).doc(id).get())
    if(existing){assert(existing.inputHash===inputHash,409,'请求内容已变化，请重新提交');await tx.commit();return {id,total:existing.total}}
    const kind=b.kind==='COPY'?'MARKETING_COPY':'MARKETING_IMAGE',quotaId=ai.hash(request.user.id,kind,state.day(Date.now())),q=ai.object(await tx.collection('mts_ai_quota').doc(quotaId).get()),limit=b.kind==='COPY'?100:cfg.marketingLimit
    assert((q?.used||0)+inputs.length<=limit,429,'今日生成额度不足')
    if(q)await tx.collection('mts_ai_quota').doc(quotaId).update({used:q.used+inputs.length})
    else await tx.collection('mts_ai_quota').add({_id:quotaId,ownerId:request.user.id,kind,day:state.day(Date.now()),used:inputs.length})
    for(let index=0;index<inputs.length;index++){
      const jid=ai.hash(id,index),input=inputs[index]
      await tx.collection('mts_ai_job').add({_id:jid,id:jid,ownerId:request.user.id,cinemaId:c.cinemaId,campaignId:c.id,batchId:id,kind,input,inputHash:ai.hash(JSON.stringify(input)),status:'QUEUED',attempts:0,providerCalls:0,createdAt:Date.now(),updatedAt:Date.now(),leaseUntil:0,leaseToken:''})
    }
    await tx.collection(TABLE).add({_id:id,id,type:'BATCH',campaignId:c.id,cinemaId:c.cinemaId,inputHash,total:inputs.length,createdAt:now()});await tx.commit()
    return {id,total:inputs.length}
  }catch(e){await tx.rollback().catch(()=>{});throw e}
}
async function version(request,db,id){const v=await ai.get(db,TABLE,id);assert(v?.type==='VERSION',404,'内容版本不存在');const c=await campaign(request,db,v.campaignId);return {v,c}}
function beijingDate(value){if(!value)return NaN;return Date.parse(/(?:Z|[+-]\d\d:\d\d)$/.test(value)?value:value+'+08:00')}
async function placements(request,db){
  const {slot,cinemaId}=request.query;assert(['HOME','CINEMA'].includes(slot),400,'展示位置无效');if(slot==='CINEMA')numericId(cinemaId)
  const rows=await db.collection(TABLE).where({type:'PUBLICATION',slot,status:'PUBLISHED',...(slot==='CINEMA'?{cinemaId:Number(cinemaId)}:{})}).orderBy('createdAt','desc').limit(100).get()
  const output=[]
  for(const p of rows.data){if(beijingDate(p.startAt)>Date.now()||beijingDate(p.endAt)<=Date.now())continue
    const v=await ai.get(db,TABLE,p.contentVersionId),c=await ai.get(db,TABLE,p.campaignId),movie=v&&c?.movieSnapshot.find(m=>m.id===v.movieId)
    if(!v||!c||!movie)continue
    const actual=(await db.collection('mts_movie').where({id:movie.id,deleted:0}).limit(1).get()).data?.[0]
    if(!actual||actual.status===0)continue
    output.push({id:p.id,title:v.payload.title,copy:v.payload.copy,imageUrl:'/api/marketing/assets/'+v.payload.assetId,cinemaId:c.cinemaId,cinemaName:c.cinemaName,movieId:movie.id,movieTitle:actual.title,targetUrl:'/movie/'+movie.id,endAt:p.endAt})
    if(output.length>=6)break
  }
  return output
}
async function handle(request,db){
  const {path,method,body}=request
  if(path==='/marketing/placements'&&method==='GET')return success(await placements(request,db))
  let match=path.match(/^\/marketing\/assets\/([\w-]+)$/)
  if(match&&method==='GET'){
    const a=await ai.get(db,TABLE,match[1]);assert(a?.type==='ASSET',404,'素材不存在')
    const pubs=await db.collection(TABLE).where({campaignId:a.campaignId,type:'PUBLICATION',status:'PUBLISHED'}).limit(100).get()
    let active=false;for(const p of pubs.data)if(beijingDate(p.startAt)<=Date.now()&&beijingDate(p.endAt)>Date.now()){const v=await ai.get(db,TABLE,p.contentVersionId);if(v?.payload.assetId===a.id){active=true;break}}
    assert(active,404,'素材尚未发布或已到期');return ai.binary(require('./ai-preview')(await ai.readPrivate(a.storageKey)))
  }
  requireUser(request,['ADMIN','CINEMA_ADMIN']);if(request.user.role==='CINEMA_ADMIN')scopeCinemaId(request,null)
  const base='/admin/marketing'
  if(path===base+'/capabilities'&&method==='GET'){const c=ai.config();return success({imageConfigured:c.imageConfigured,textConfigured:c.textConfigured,executionMode:c.executionMode,posterContentRequired:true,imageModel:c.textImageModel,imageProvider:'zhipu',maxImagesPerBatch:c.batchLimit,dailyImageLimit:c.marketingLimit,styles:['自由创作'],ratios:ai.ratios})}
  if(path===base+'/campaigns'){
    if(method==='POST')return success(await create(request,db))
    if(method==='GET'){const cinemaId=scopeCinemaId(request,null);const rows=await db.collection(TABLE).where({type:'CAMPAIGN',...(cinemaId?{cinemaId}:{})}).orderBy('createdAt','desc').limit(100).get();return success(rows.data.map(baseView))}
  }
  match=path.match(/^\/admin\/marketing\/campaigns\/([\w-]+)(?:\/(batches|versions))?$/)
  if(match){
    const c=await campaign(request,db,match[1])
    if(!match[2]&&method==='GET')return success(await detail(request,db,c.id))
    if(match[2]==='batches'&&method==='POST'){const batch=await generate(request,db,c.id);await demand.advance(db,{campaignId:c.id,batchId:batch.id});return success(batch)}
    if(match[2]==='versions'&&method==='POST'){
      const movieId=numericId(body.movieId),asset=await ai.get(db,TABLE,body.assetId)
      assert(c.movieSnapshot.some(m=>m.id===movieId)&&asset?.type==='ASSET'&&asset.campaignId===c.id&&asset.movieId===movieId,400,'素材与影片不匹配')
      const payload={title:ai.text(body.title,60,'标题',true),copy:ai.text(body.copy,300,'短文案',true),assetId:asset.id,cta:'查看场次'},tx=await db.startTransaction()
      try{const current=ai.object(await tx.collection(TABLE).doc(c.id).get()),version=(current.versionSeq||0)+1,id=crypto.randomUUID(),v={_id:id,id,type:'VERSION',campaignId:c.id,cinemaId:c.cinemaId,movieId,version,status:'DRAFT',payload,createdAt:now()};await tx.collection(TABLE).doc(c.id).update({versionSeq:version});await tx.collection(TABLE).add(v);await tx.commit();return success(baseView(v))}catch(e){await tx.rollback().catch(()=>{});throw e}
    }
  }
  match=path.match(/^\/admin\/marketing\/versions\/([\w-]+)\/(submit-review|review|publish)$/)
  if(match&&method==='POST'){
    const {v,c}=await version(request,db,match[1]),action=match[2]
    if(action==='publish'){
      requireUser(request,['ADMIN']);assert(['APPROVED','PUBLISHED'].includes(v.status),409,'仅可发布审核通过的版本')
      assert(['HOME','CINEMA'].includes(body.slot),400,'展示位置无效')
      const start=body.startAt?beijingDate(body.startAt):Date.now(),end=beijingDate(body.endAt)
      assert(Number.isFinite(start)&&Number.isFinite(end)&&end>start&&end>Date.now(),400,'展示有效期无效')
      const key=ai.text(body.idempotencyKey,80,'发布请求标识',true),id=ai.hash(v.id,key),payload={contentVersionId:v.id,slot:body.slot,startAt:body.startAt?new Date(start).toISOString():null,endAt:new Date(end).toISOString()},tx=await db.startTransaction()
      try{
        const existing=ai.object(await tx.collection(TABLE).doc(id).get());if(existing){assert(existing.requestHash===ai.hash(payload),409,'发布请求已变化');await tx.commit();return success(baseView(existing))}
        const current=ai.object(await tx.collection(TABLE).doc(v.id).get());assert(['APPROVED','PUBLISHED'].includes(current?.status),409,'版本审核状态已变化')
        const p={_id:id,id,type:'PUBLICATION',campaignId:c.id,cinemaId:c.cinemaId,...payload,startAt:payload.startAt||new Date(start).toISOString(),status:'PUBLISHED',requestHash:ai.hash(payload),createdAt:now()}
        await tx.collection(TABLE).add(p);await tx.collection(TABLE).doc(v.id).update({status:'PUBLISHED'});await tx.commit();await log(db,c,request.user.id,'PUBLISHED');return success(baseView(p))
      }catch(e){await tx.rollback().catch(()=>{});throw e}
    }
    if(action==='review')requireUser(request,['ADMIN'])
    const expected=action==='submit-review'?'DRAFT':'PENDING_REVIEW',status=action==='submit-review'?'PENDING_REVIEW':body.approve===true?'APPROVED':'REJECTED'
    assert(v.status===expected,409,'版本状态已变化');const reason=action==='review'?ai.text(body.reason,300,'审核原因',!body.approve):''
    const r=await db.collection(TABLE).where({_id:v.id,status:expected}).update({status,reviewReason:reason,reviewerId:request.user.id});assert(r.updated===1,409,'版本状态已变化');await log(db,c,request.user.id,status,reason);return success()
  }
  match=path.match(/^\/admin\/marketing\/publications\/([\w-]+)\/offline$/)
  if(match&&method==='POST'){requireUser(request,['ADMIN']);const p=await ai.get(db,TABLE,match[1]);assert(p?.type==='PUBLICATION',404,'发布记录不存在');await db.collection(TABLE).doc(p.id).update({status:'OFFLINE'});return success()}
  match=path.match(/^\/admin\/marketing\/assets\/([\w-]+)\/file$/)
  if(match&&method==='GET'){const a=await ai.get(db,TABLE,match[1]);assert(a?.type==='ASSET',404,'素材不存在');await campaign(request,db,a.campaignId);return ai.fileResponse(await ai.readPrivate(a.storageKey),request.query)}
  match=path.match(/^\/admin\/marketing\/tasks\/([\w-]+)\/retry$/)
  if(match&&method==='POST'){const j=await ai.get(db,'mts_ai_job',match[1]);assert(j?.campaignId,404,'任务不存在');await campaign(request,db,j.campaignId);await ai.retry(db,j,body.acknowledgePossibleCharge===true);await demand.advance(db,{campaignId:j.campaignId,id:j.id});return success({id:j.id})}
  throw new ApiError(404,'接口不存在')
}
module.exports={handle,placements,beijingDate,generate}
