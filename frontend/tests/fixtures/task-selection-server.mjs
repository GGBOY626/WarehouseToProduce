// Isolated fixture for task selection regression checks against the real frontend build.
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { resolve, extname } from 'node:path';
import { fileURLToPath } from 'node:url';
const dist = fileURLToPath(new URL('../../dist/', import.meta.url));
const products = [1,2].map(id => ({ id, name: id === 1 ? 'QA Material' : 'QA Output', materialCode: `C${id}A`, materialCodes: [`C${id}A`,`C${id}B`], materialBatches: [`B${id}A`,`B${id}B`], productionNumbers: [`N${id}A`,`N${id}B`], active: true, baseUnit: 'unit', quantityUnknown: false, movementDirection: id===1 ? 'WAREHOUSE_TO_PRODUCTION' : 'PRODUCTION_TO_WAREHOUSE', cartonSpecs: [], boxPackings: [] }));
const selection = productId => ({productId, materialCodes:[`C${productId}B`],materialBatches:[`B${productId}B`],productionNumbers:[`N${productId}B`]});
const target = {...selection(1),productName:'QA Material',materialCode:'C1A',baseUnit:'unit',targetQuantity:100,completedQuantity:0,progressPercent:0,issuedQuantity:0,returnedQuantity:0,shortageQuantity:100,unknownQuantityCount:0,materialTarget:true,batches:[]};
const output = {...selection(2),productName:'QA Output',materialCode:'C2A',baseUnit:'unit',targetQuantity:100,receivedQuantity:0,progressPercent:0,unknownQuantityCount:0,provisional:true};
let task = {id:91,name:'QA Task',productId:1,productName:'QA Material',plannedDate:'2026-11-11',status:'IN_PROGRESS',targets:[target],output,movements:[],photos:[],progressPercent:0};
createServer(async(req,res)=>{
 const url=new URL(req.url,'http://localhost');
 const json=value=>{res.setHeader('Content-Type','application/json; charset=utf-8');res.end(JSON.stringify(value));};
 if(url.pathname==='/api/auth/status')return json({authenticated:true,username:'QA'});
 if(url.pathname==='/api/products')return json(products.filter(p=>!url.searchParams.get('direction')||p.movementDirection===url.searchParams.get('direction')));
 if(url.pathname==='/api/production-tasks/product-choices')return json([url.searchParams.get('direction')==='PRODUCTION_TO_WAREHOUSE'?2:1]);
 if(url.pathname==='/api/production-tasks/linkable')return json([{id:91,name:'QA Task',productName:'QA Task',status:'IN_PROGRESS',outputProductId:2,nextPalletNo:1,targets:task.targets,output:task.output}]);
 if(url.pathname==='/api/production-tasks/91' && req.method==='PUT'){
  let body='';for await(const chunk of req)body+=chunk;
  const value=JSON.parse(body);
  task={...task,...value,output:{...output,materialCodes:value.outputMaterialCodes,materialBatches:value.outputMaterialBatches,productionNumbers:value.outputProductionNumbers}};
  return json(task);
 }
 if(url.pathname==='/api/production-tasks/91')return json(task);
 if(url.pathname==='/api/production-tasks')return json([task]);
 if(url.pathname.startsWith('/api/'))return json([]);
 const file=url.pathname.startsWith('/assets/')?resolve(dist,'.'+url.pathname):resolve(dist,'index.html');
 try {res.setHeader('Content-Type',({'.js':'text/javascript','.css':'text/css','.html':'text/html'})[extname(file)]||'application/octet-stream');res.end(await readFile(file));}catch{res.writeHead(404);res.end();}
}).listen(5193,'127.0.0.1',()=>console.log('Task selections fixture on 5193'));
