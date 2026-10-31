'use strict'
// Uses curl so local requests obey the user's explicit HTTP proxy preference.
// Tokens are saved only under ignored .local; reports never contain credentials.
const fs=require('fs'),path=require('path'),crypto=require('crypto'),{execFileSync}=require('child_process')
const {recognize,decode}=require('./verify-cloud-login')
const root=path.resolve(__dirname,'..'),local=path.join(root,'.local'),api='https://fc-mp-e7a16837-eb5c-4898-8fbd-e967f4d747d6.next.bspapp.com/api'
const sessionFile=path.join(local,'ai-cloud-smoke-private.json'),reportFile=path.join(local,'ai-cloud-smoke.json')
function request(url,token,{method='GET',body,form,raw=false}={}){
  if(form){body={fields:{},files:[]};for(const [key,value]of Object.entries(form)){
    if(value.file)body.files.push({name:key,filename:path.basename(value.file),mime:value.type||'application/octet-stream',base64:fs.readFileSync(value.file).toString('base64')})
    else body.fields[key]=String(value)
  }form=null}
  const args=['--proxy',process.env.HTTPS_PROXY||'http://127.0.0.1:7890','--noproxy','localhost,127.0.0.1,::1','--silent','--show-error','--max-time','115','--request',method]
  if(token)args.push('--header','Authorization: Bearer '+token)
  args.push('--header','X-Release-Probe: 1')
  if(body!==undefined)args.push('--header','Content-Type: application/json','--data-binary','@-')
  for(const [key,value]of Object.entries(form||{}))args.push(value.file?'--form':'--form-string',key+'='+(value.file?'@'+value.file+(value.type?';type='+value.type:''):String(value)))
  args.push(api+url)
  const bytes=execFileSync('curl.exe',args,{input:body===undefined?undefined:JSON.stringify(body),maxBuffer:30*1024*1024})
  if(raw){
    let manifest;try{manifest=JSON.parse(bytes.toString('utf8'))}catch{}
    if(manifest?.code===200&&Number.isInteger(manifest.data?.parts)){
      if(manifest.data.parts<1||manifest.data.parts>42)throw new Error('Invalid image manifest')
      const all=Buffer.concat(Array.from({length:manifest.data.parts},(_,part)=>request(url+(url.includes('?')?'&':'?')+'part='+part,token,{raw:true})))
      if(all.length!==manifest.data.size)throw new Error('Incomplete image download');return all
    }return bytes
  }
  return JSON.parse(bytes.toString('utf8'))
}
function ok(result,label){if(result.code!==200)throw new Error(label+': '+result.code+' '+result.message+' '+JSON.stringify(result.diagnostic||{}));return result.data}
function login(username){
  for(let i=0;i<3;i++){
    const c=ok(request('/auth/captcha'),'captcha'),captcha=recognize(decode(c.image)),r=request('/auth/login',null,{method:'POST',body:{username,password:process.env.AI_SMOKE_PASSWORD||'admin123',captchaId:c.captchaId,captcha}})
    if(r.code===200)return {token:r.data.token,user:r.data.userInfo}
    if(!String(r.message).includes('验证码'))throw new Error('Login failed: '+r.message)
  }throw new Error('Captcha not recognized')
}
function write(session,report){fs.writeFileSync(sessionFile,JSON.stringify(session,null,2));fs.writeFileSync(reportFile,JSON.stringify(report,null,2))}
async function main(){
  const mode=process.argv[2]||'start'
  if(mode==='start'){
    if(fs.existsSync(reportFile)&&JSON.parse(fs.readFileSync(reportFile)).jobs?.length)throw new Error('A durable smoke run already exists; use poll to avoid repeated paid requests')
    const health=ok(request('/health'),'health');if(health.release!=='ai-cloud-20261031')throw new Error('Wrong release')
    const admin=login('admin'),user=login('user1'),session={admin,user},report={release:health.release,startedAt:new Date().toISOString(),checks:[],jobs:[]}
    for(const [route,account]of [['/admin/marketing/capabilities',admin],['/listing/capabilities',admin],['/posters/capabilities',user],['/souvenirs/capabilities',user]]){
      const data=ok(request(route,account.token),route);report.checks.push({route,code:200,configured:data.available??data.imageConfigured??data.textConfigured})
    }
    const template=request('/listing/templates/MOVIE?format=xlsx',admin.token,{raw:true});if(template[0]!==0x50||template[1]!==0x4b)throw new Error('Invalid XLSX template');report.checks.push({route:'XLSX template',bytes:template.length})
    const sample='【上线验收资料，仅创建草稿】电影《银幕回声》，科幻，片长108分钟，导演林风。故事讲述一位放映员整理老胶片的经历。'
    const drafts=ok(request('/listing/import',admin.token,{method:'POST',form:{kind:'MOVIE',importMode:'AI',text:sample}}),'AI listing')
    if(drafts.total!==1||drafts.drafts[0].fields.title!=='银幕回声')throw new Error('AI extraction not verified')
    const d=drafts.drafts[0];ok(request('/listing/drafts/'+d.id+'/reject',admin.token,{method:'POST',body:{version:d.version,note:'部署验收草稿，不发布到业务目录'}}),'Reject smoke draft')
    const denied=request('/listing/drafts/'+d.id,user.token);if(denied.code!==403)throw new Error('Listing permission failed')
    report.checks.push({route:'AI listing extraction and rejection',mode:drafts.extractionMode,total:drafts.total,foreignCode:denied.code})
    const dataFile=path.join(local,'ai-smoke-poster-input.json')
    const input=fs.existsSync(dataFile)?JSON.parse(fs.readFileSync(dataFile)):{requestId:crypto.randomUUID(),movieTitle:'星空中的放映室',style:'SCIFI',prompt:'合成的虚构电影主角站在星际放映室，银蓝色光影；部署验收用原创海报',ratio:'3:4'}
    fs.writeFileSync(dataFile,JSON.stringify(input));write(session,report)
    const photo=path.resolve(root,'../.local/qwen-reference-synthetic.jpg'),form={data:{file:dataFile,type:'application/json'},...(fs.existsSync(photo)?{photo:{file:photo,type:'image/jpeg'}}:{})}
    const poster=ok(request('/posters',user.token,{method:'POST',form}),'Create poster')
    const replay=ok(request('/posters',user.token,{method:'POST',form}),'Replay poster');if(replay.id!==poster.id)throw new Error('Poster replay duplicated')
    report.jobs.push({kind:'POSTER',id:poster.id,status:poster.status,hasPortrait:poster.hasPortrait})
    write(session,report)
    const cinemas=ok(request('/cinema/all',admin.token),'cinemas'),movies=ok(request('/movie/page?page=1&size=100',admin.token),'movies').records
    const movie=movies.find(m=>m.status===1)||movies.find(m=>m.status===2)
    const campaign=ok(request('/admin/marketing/campaigns',admin.token,{method:'POST',body:{cinemaId:cinemas[0].id,name:'AI升级上线验收（未发布）',brief:'根据真实影片资料创作影院宣传文案，不虚构活动、价格、日期或优惠',audience:'电影观众',movieIds:[movie.id],styles:['自由创作'],ratios:['3:4']}}),'Campaign')
    session.campaignId=campaign.id
    write(session,report)
    ok(request('/admin/marketing/campaigns/'+campaign.id+'/batches',admin.token,{method:'POST',body:{kind:'COPY',requestKey:crypto.randomUUID()}}),'Marketing copy batch')
    const detail=ok(request('/admin/marketing/campaigns/'+campaign.id,admin.token),'Campaign detail')
    for(const task of detail.tasks)report.jobs.push({kind:'MARKETING_COPY',id:task.id,status:task.status})
    const orders=ok(request('/order/my?page=1&size=100',user.token),'orders').records
    const order=orders.find(o=>o.status===1)
    if(order){const souvenir=ok(request('/souvenirs/orders/'+order.orderNo,user.token,{method:'POST',body:{note:'把这一场光影收藏起来。'}}),'Souvenir');session.orderNo=order.orderNo;report.jobs.push({kind:'SOUVENIR',id:souvenir.id,status:souvenir.status})}
    else report.checks.push({route:'SOUVENIR real generation',skipped:'No paid order on test account'})
    write(session,report);console.log(JSON.stringify(report,null,2));return
  }
  const session=JSON.parse(fs.readFileSync(sessionFile)),report=JSON.parse(fs.readFileSync(reportFile))
  if(mode==='poll'){
    const posters=ok(request('/posters',session.user.token),'Posters'),detail=ok(request('/admin/marketing/campaigns/'+session.campaignId,session.admin.token),'Marketing detail')
    const souvenirs=session.orderNo?ok(request('/souvenirs/orders/'+session.orderNo,session.user.token),'Souvenirs'):[]
    for(const job of report.jobs){const current=(job.kind==='POSTER'?posters:job.kind==='SOUVENIR'?souvenirs:detail.tasks).find(j=>j.id===job.id);job.status=current?.status||'MISSING';if(current?.errorMessage)job.errorMessage=current.errorMessage;else delete job.errorMessage
      if(job.status==='SUCCEEDED'&&!job.verified){
        if(job.kind==='POSTER'||job.kind==='SOUVENIR'){
          const route='/'+(job.kind==='POSTER'?'posters':'souvenirs')+'/'+job.id+'/file',bytes=request(route,session.user.token,{raw:true});if(!bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))throw new Error('Invalid generated PNG')
          fs.writeFileSync(path.join(local,'cloud-'+job.kind.toLowerCase()+'.png'),bytes);job.bytes=bytes.length;job.width=bytes.readUInt32BE(16);job.height=bytes.readUInt32BE(20)
          const denied=request(route,session.admin.token);if(denied.code!==404)throw new Error('Private image ownership failed');job.foreignCode=denied.code
        }else if(job.kind==='MARKETING_IMAGE'){
          const assetId=current.result?.assetId;if(!assetId)throw new Error('No marketing asset')
          const bytes=request('/admin/marketing/assets/'+assetId+'/file',session.admin.token,{raw:true});if(!bytes.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))throw new Error('Invalid marketing PNG')
          fs.writeFileSync(path.join(local,'cloud-marketing.png'),bytes);job.assetId=assetId;job.bytes=bytes.length;job.width=bytes.readUInt32BE(16);job.height=bytes.readUInt32BE(20)
        }else{if(!current.result?.titles?.length)throw new Error('No marketing copy');job.titles=current.result.titles}
        job.verified=true
      }
    }
    report.updatedAt=new Date().toISOString();write(session,report);console.log(JSON.stringify(report.jobs,null,2));return
  }
  throw new Error('Use start or poll')
}
module.exports={request,ok,login}
if(require.main===module)main().catch(e=>{console.error('AI cloud verification failed:',e.message);process.exitCode=1})
