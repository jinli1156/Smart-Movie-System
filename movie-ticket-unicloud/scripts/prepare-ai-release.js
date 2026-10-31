'use strict'
const fs=require('fs'),path=require('path'),crypto=require('crypto')
const root=path.resolve(__dirname,'..'),cloud=path.join(root,'movie-ticket-unicloud-cloud/uniCloud-aliyun/cloudfunctions')
const api=path.join(cloud,'movie-api'),worker=path.join(cloud,'movie-ai-worker'),runtime=path.join(worker,'runtime')
fs.mkdirSync(runtime,{recursive:true})
const names=['core.js','read-env.js','secrets.json','ai-state.js','ai-common.js','ai-worker.js','ai-demand-runtime.js','ai-render.js']
const checksums={}
for(const name of names){const bytes=fs.readFileSync(path.join(api,name));fs.writeFileSync(path.join(runtime,name),bytes);checksums[name]=crypto.createHash('sha256').update(bytes).digest('hex')}
const font=path.join(root,'.local/ai-vendor/NotoSansCJKsc-Regular.ttf')
if(!fs.existsSync(font))throw new Error('Run scripts/prepare-ai-vendor.py first (Noto font and PDF.js are required)')
fs.mkdirSync(path.join(runtime,'fonts'),{recursive:true});fs.copyFileSync(font,path.join(runtime,'fonts/NotoSansCJKsc-Regular.ttf'))
const obsoleteFont=path.join(runtime,'fonts/NotoSansCJKsc-Regular.otf')
if(fs.existsSync(obsoleteFont))fs.unlinkSync(obsoleteFont)
fs.copyFileSync(path.join(root,'.local/ai-vendor/OFL.txt'),path.join(runtime,'fonts/OFL.txt'))
const pdfRoot=path.join(root,'.local/ai-vendor/node_modules/pdfjs-dist'),pdfOut=path.join(api,'vendor/pdfjs')
fs.mkdirSync(pdfOut,{recursive:true})
for(const name of ['pdf.mjs','pdf.worker.mjs'])fs.copyFileSync(path.join(pdfRoot,'legacy/build',name),path.join(pdfOut,name))
fs.copyFileSync(path.join(pdfRoot,'LICENSE'),path.join(pdfOut,'LICENSE'))
fs.writeFileSync(path.join(root,'.local/ai-runtime-manifest.json'),JSON.stringify({release:'ai-cloud-20261031',checksums},null,2))
console.log('AI worker runtime, font and PDF engine prepared; private configuration stays server-side.')
