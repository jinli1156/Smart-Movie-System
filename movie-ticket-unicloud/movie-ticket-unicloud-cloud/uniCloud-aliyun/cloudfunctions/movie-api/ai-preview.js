'use strict'
const {PNG}=require('pngjs')
const {assert}=require('./core')
module.exports=function preview(bytes){
  const source=PNG.sync.read(bytes),max=700*1024
  if(bytes.length<=max)return bytes
  let scale=Math.min(1,900/Math.max(source.width,source.height))
  for(let attempt=0;attempt<12;attempt++,scale*=.82){
    const width=Math.max(64,Math.round(source.width*scale)),height=Math.max(64,Math.round(source.height*scale)),out=new PNG({width,height})
    for(let y=0;y<height;y++)for(let x=0;x<width;x++){
      const sx=(x+.5)*source.width/width-.5,sy=(y+.5)*source.height/height-.5,x0=Math.max(0,Math.floor(sx)),y0=Math.max(0,Math.floor(sy)),x1=Math.min(source.width-1,x0+1),y1=Math.min(source.height-1,y0+1),fx=Math.max(0,sx-x0),fy=Math.max(0,sy-y0)
      for(let c=0;c<4;c++){const a=source.data[(y0*source.width+x0)*4+c]*(1-fx)+source.data[(y0*source.width+x1)*4+c]*fx,b=source.data[(y1*source.width+x0)*4+c]*(1-fx)+source.data[(y1*source.width+x1)*4+c]*fx;out.data[(y*width+x)*4+c]=Math.round(a*(1-fy)+b*fy)}
    }
    const encoded=PNG.sync.write(out);if(encoded.length<=max)return encoded
  }
  assert(false,502,'宣传图片预览暂不可用')
}
