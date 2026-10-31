'use strict'
// Optimistic transaction fixture: commit conflicts instead of allowing both
// snapshots to reserve the same quota or publish the same reviewed draft.
module.exports=function database(seed={}){
  let tables=structuredClone(seed),revision=0
  const cmd={in:v=>({op:'in',v}),lt:v=>({op:'lt',v}),gte:v=>({op:'gte',v}),inc:v=>({op:'inc',v})}
  const matches=(row,filter)=>Object.entries(filter).every(([k,v])=>v?.op==='in'?v.v.includes(row[k]):v?.op==='lt'?row[k]<v.v:v?.op==='gte'?row[k]>=v.v:row[k]===v)
  function collection(name,local,transaction=false,onWrite=()=>{revision++}){
    local[name]||=[];let filter={},skip=0,limit=1000,sort
    const matching=()=>local[name].filter(r=>matches(r,filter)),change=(row,data)=>{for(const[k,v]of Object.entries(data))row[k]=v?.op==='inc'?(row[k]||0)+v.v:structuredClone(v)}
    const q={
      where(v){filter=v;return q},skip(v){skip=v;return q},limit(v){limit=v;return q},orderBy(k,d){sort={k,d};return q},
      async get(){let rows=matching();if(sort)rows=rows.slice().sort((a,b)=>(a[sort.k]>b[sort.k]?1:a[sort.k]<b[sort.k]?-1:0)*(sort.d==='desc'?-1:1));return{data:structuredClone(rows.slice(skip,skip+limit))}},
      async count(){return{total:matching().length}},
      async add(value){const values=Array.isArray(value)?value:[value];for(const item of values){if(local[name].some(r=>r._id===item._id&&item._id))throw new Error('duplicate _id');local[name].push({_id:item._id||require('crypto').randomUUID(),...structuredClone(item)})}onWrite();return{id:values[0]._id}},
      async update(data){const rows=matching();rows.forEach(r=>change(r,data));if(rows.length)onWrite();return{updated:rows.length}},
      async updateAndReturn(data){const row=matching()[0];if(row){change(row,data);onWrite()}return{doc:structuredClone(row)}},
      doc(id){return{
        async get(){const row=local[name].find(r=>r._id===id);return{data:transaction?structuredClone(row):row?[structuredClone(row)]:[]}},
        async update(data){const row=local[name].find(r=>r._id===id);if(row){change(row,data);onWrite()}return{updated:row?1:0}},
        async remove(){const n=local[name].length;local[name]=local[name].filter(r=>r._id!==id);onWrite();return{deleted:n-local[name].length}}
      }}
    };return q
  }
  return{
    get tables(){return tables},command:cmd,collection:name=>collection(name,tables),
    async startTransaction(){const start=revision,local=structuredClone(tables);let dirty=false;return{
      collection:name=>collection(name,local,true,()=>{dirty=true}),
      async commit(){if(revision!==start)throw new Error('transaction conflict');if(dirty){tables=local;revision++}},async rollback(){}
    }}
  }
}
