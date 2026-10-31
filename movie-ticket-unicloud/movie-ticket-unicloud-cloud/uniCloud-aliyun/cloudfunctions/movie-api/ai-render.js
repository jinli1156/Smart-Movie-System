'use strict'
const { Readable, Writable }=require('stream')
const { PNG }=require('pngjs')
const jpeg=require('jpeg-js')
const {assert}=require('./core')
const {imageMime}=require('./ai-common')
function decode(bytes){
  const mime=imageMime(bytes)
  let image
  if(mime==='image/png'){
    const width=bytes.readUInt32BE(16),height=bytes.readUInt32BE(20)
    assert(width>=64&&height>=64&&width*height<=16777216,502,'图片尺寸无效')
    image=PNG.sync.read(bytes)
  }else image=jpeg.decode(bytes,{useTArray:true,maxResolutionInMP:17,maxMemoryUsageInMB:128})
  assert(image.width>=64&&image.height>=64&&image.width*image.height<=16777216,502,'图片尺寸无效')
  return image
}
function png(bytes){const image=decode(bytes);return{bytes:PNG.sync.write(image),width:image.width,height:image.height}}
let font
async function souvenir(bytes,facts,plan){
  const P=require('pureimage'),path=require('path')
  if(!font){font=P.registerFont(path.join(__dirname,'fonts/NotoSansCJKsc-Regular.ttf'),'Noto');await font.load()}
  const raw=decode(bytes),background=P.make(raw.width,raw.height);background.data.set(raw.data)
  const width=900,height=1300,image=P.make(width,height),g=image.getContext('2d')
  const scale=Math.max(width/raw.width,height/raw.height),w=raw.width*scale,h=raw.height*scale
  g.drawImage(background,(width-w)/2,(height-h)/2,w,h)
  // All order facts and the disclaimer are drawn by code, never by the model.
  for(let y=350;y<height;y++){const alpha=Math.min(.97,.15+(y-350)/650);g.fillStyle=`rgba(6,12,36,${alpha})`;g.fillRect(0,y,width,1)}
  g.strokeStyle='#6de2ff';g.lineWidth=2;g.strokeRect(32,32,width-64,height-64)
  function line(text,x,y,size=23,color='#edf5ff',max=width-130){
    g.font=size+'px Noto';g.fillStyle=color
    let displayed='';for(const c of String(text||'')){if(g.measureText(displayed+c).width>max){displayed+='…';break}displayed+=c}g.fillText(displayed,x,y)
  }
  function lines(value,y,size,maxLines=2,color='#edf5ff'){
    g.font=size+'px Noto';const rows=[];let row=''
    for(const c of String(value||'')){if(g.measureText(row+c).width>width-130){rows.push(row);row='';if(rows.length===maxLines)break}row+=c}
    if(row&&rows.length<maxLines)rows.push(row);rows.forEach((r,i)=>line(r,65,y+i*(size+12),size,color))
  }
  line('响指电影 / AI 观影纪念票',65,90,23,'#6de2ff')
  line('STELLAR ARCHIVE / 星空科幻收藏',65,130,18,'#b09fff')
  line('把这一场光影，存入宇宙。',65,465,23,'#6de2ff')
  lines(facts.movieTitle,545,46,2)
  lines(plan.headline,735,32,2)
  lines(plan.caption,840,23,2,'#bfcded')
  g.strokeStyle='#6b8cb6';g.beginPath();g.moveTo(65,940);g.lineTo(835,940);g.stroke()
  line(facts.cinemaName,65,990,24)
  line('场次  '+String(facts.showtime).replace('T',' ').replace(/(?:Z|[+-]\d\d:\d\d)$/,''),65,1035,22,'#bfcded')
  line('影厅  '+facts.hallName,65,1080,22,'#bfcded')
  line('座位  '+(typeof facts.seats==='string'?facts.seats:JSON.stringify(facts.seats)),65,1125,22,'#bfcded')
  line('AI 创作纪念 · 非入场凭证',65,1225,22,'#6de2ff')
  const chunks=[],stream=new Writable({write(b,enc,next){chunks.push(b);next()}})
  await P.encodePNGToStream(image,stream)
  return{bytes:Buffer.concat(chunks),width,height}
}
module.exports={png,souvenir,decode}
