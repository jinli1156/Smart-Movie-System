'use strict'
const { unzipSync, zipSync, strToU8 } = require('fflate')
const { XMLParser } = require('fast-xml-parser')
const { assert, ApiError } = require('./core')
const { binary } = require('./ai-common')
const xml = new XMLParser({ ignoreAttributes:false,attributeNamePrefix:'@',parseTagValue:false,trimValues:false })
const array=v=>v==null?[]:Array.isArray(v)?v:[v]
function escape(s){return String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]))}
function unzip(bytes) {
  let total=0
  return unzipSync(bytes,{filter:f=>{
    assert(f.originalSize>=0&&f.originalSize<=12*1024*1024,400,'文档解压后过大')
    total+=f.originalSize;assert(total<=20*1024*1024,400,'文档解压后过大')
    return /^(xl\/(sharedStrings|worksheets\/sheet\d+)\.xml|word\/document\.xml)$/.test(f.name)
  }})
}
function textNodes(value,key='t'){
  if(value==null)return ''
  if(Array.isArray(value))return value.map(v=>textNodes(v,key)).join('')
  if(typeof value!=='object')return String(value)
  if(value[key]!=null)return textNodes(value[key],key)
  return Object.entries(value).filter(([k])=>!k.startsWith('@')).map(([,v])=>textNodes(v,key)).join('')
}
function csv(rows){return rows.map(row=>row.map(v=>'"'+String(v??'').replace(/"/g,'""')+'"').join(',')).join('\r\n')}
function parseCsv(value){
  value=value.replace(/^\uFEFF/,'');const rows=[];let row=[],cell='',quoted=false
  for(let i=0;i<value.length;i++){
    const c=value[i]
    if(c==='"'){if(quoted&&value[i+1]==='"'){cell+='"';i++}else quoted=!quoted}
    else if(!quoted&&(c===','||c==='\t')){row.push(cell);cell=''}
    else if(!quoted&&(c==='\n'||c==='\r')){if(c==='\r'&&value[i+1]==='\n')i++;row.push(cell);if(row.some(v=>v.trim()))rows.push(row);row=[];cell=''}
    else cell+=c
  }
  assert(!quoted,400,'CSV引号未闭合');row.push(cell);if(row.some(v=>v.trim()))rows.push(row)
  return rows
}
function records(value,fields){
  const trimmed=value.trim().replace(/^\uFEFF/,'')
  if(trimmed.startsWith('{')||trimmed.startsWith('[')){
    let parsed;try{parsed=JSON.parse(trimmed)}catch{throw new ApiError(400,'JSON格式无效')}
    return Array.isArray(parsed)?parsed:Array.isArray(parsed.records)?parsed.records:[parsed]
  }
  const rows=parseCsv(trimmed);assert(rows.length>=2,400,'模板需要表头和至少一行资料')
  const keys=rows[0].map(label=>fields.find(f=>f.key===label.trim()||f.label===label.trim())?.key)
  assert(keys.some(Boolean),400,'未识别模板字段，请使用下载的模板')
  return rows.slice(1).map(row=>Object.fromEntries(keys.map((k,i)=>[k,row[i]??'']).filter(([k])=>k)))
}
async function read(file){
  const name=file.filename.toLowerCase()
  if(/\.(txt|md|csv|json)$/.test(name))return new TextDecoder('utf-8',{fatal:true}).decode(file.bytes)
  if(name.endsWith('.xlsx')){
    const entries=unzip(file.bytes),strings=entries['xl/sharedStrings.xml']?array(xml.parse(entries['xl/sharedStrings.xml']).sst?.si).map(v=>textNodes(v)):[]
    const sheets=Object.keys(entries).filter(k=>/^xl\/worksheets\/sheet\d+\.xml$/.test(k)).sort()
    assert(sheets.length,400,'Excel没有可读工作表')
    const rows=[]
    for(const key of sheets){
      for(const row of array(xml.parse(entries[key]).worksheet?.sheetData?.row)){
        const cells=[]
        for(const c of array(row.c)){
          const ref=String(c['@r']||'A1').match(/^[A-Z]+/)[0];let index=0;for(const ch of ref)index=index*26+ch.charCodeAt(0)-64
          assert(index<=100,400,'Excel列数过多')
          cells[index-1]=c['@t']==='s'?strings[Number(c.v)]||'':c['@t']==='inlineStr'?textNodes(c.is):c.v??''
        }
        if(cells.some(v=>String(v).trim()))rows.push(Array.from(cells,v=>v??''))
        assert(rows.length<=200,400,'Excel行数过多')
      }
    }
    return csv(rows)
  }
  if(name.endsWith('.docx')){
    const entry=unzip(file.bytes)['word/document.xml'];assert(entry,400,'Word文档格式无效')
    const document=xml.parse(entry);return array(document['w:document']?.['w:body']?.['w:p']).map(p=>textNodes(p,'w:t')).join('\n')
  }
  if(name.endsWith('.pdf')){
    if(!Promise.withResolvers)Promise.withResolvers=()=>{let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});return{promise,resolve,reject}}
    const pdfjs=await import('./vendor/pdfjs/pdf.mjs')
    const task=pdfjs.getDocument({data:new Uint8Array(file.bytes),useSystemFonts:false,isEvalSupported:false,disableFontFace:true})
    try{const doc=await task.promise;assert(doc.numPages<=30,400,'PDF最多30页');const pages=[];for(let i=1;i<=doc.numPages;i++){const page=await doc.getPage(i);const content=await page.getTextContent();pages.push(content.items.map(t=>t.str+(t.hasEOL?'\n':' ')).join(''))}const text=pages.join('\n');assert(text.trim(),400,'扫描PDF请导出图片后使用AI导入');return text}finally{await task.destroy()}
  }
  throw new ApiError(400,'支持TXT、MD、CSV、JSON、XLSX、DOCX和文字PDF；图片请使用AI导入')
}
function template(kind,fields,format){
  const example=Object.fromEntries(fields.map(f=>[f.key,f.key==='status'?0:'']))
  if(format==='json')return binary(Buffer.from(JSON.stringify([example],null,2)),'application/json',kind+'.json')
  if(format==='csv')return binary(Buffer.from('\uFEFF'+csv([fields.map(f=>f.key),fields.map(f=>example[f.key])])),'text/csv; charset=utf-8',kind+'.csv')
  const rows=[fields.map(f=>f.key),fields.map(f=>example[f.key])]
  const sheet='<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>'+rows.map((r,i)=>'<row r="'+(i+1)+'">'+r.map((v,j)=>'<c r="'+String.fromCharCode(65+j)+(i+1)+'" t="inlineStr"><is><t>'+escape(v)+'</t></is></c>').join('')+'</row>').join('')+'</sheetData></worksheet>'
  const files={
    '[Content_Types].xml':'<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>',
    '_rels/.rels':'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>',
    'xl/workbook.xml':'<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="'+kind+'" sheetId="1" r:id="rId1"/></sheets></workbook>',
    'xl/_rels/workbook.xml.rels':'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>',
    'xl/worksheets/sheet1.xml':sheet }
  return binary(Buffer.from(zipSync(Object.fromEntries(Object.entries(files).map(([k,v])=>[k,strToU8(v)])))),'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',kind+'.xlsx')
}
module.exports={read,records,template,parseCsv}
