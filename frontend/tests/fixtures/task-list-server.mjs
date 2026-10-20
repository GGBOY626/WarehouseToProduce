// Isolated visual QA fixture. Serves the real frontend build; never connects to business services.
// Run: node tests/fixtures/task-list-server.mjs (from frontend), then visit localhost:5192/production-tasks.
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve, extname, sep } from 'node:path';

const dist=resolve(fileURLToPath(new URL('../../dist/',import.meta.url)));
const material=(name,shortage,size=400,id=1)=>({productId:id,productName:name,materialCode:`QA-${id}`,baseUnit:'个',targetQuantity:200000,completedQuantity:200000-shortage,progressPercent:42.35,issuedQuantity:200000-shortage,returnedQuantity:0,shortageQuantity:shortage,shortageCartonSize:size,unknownQuantityCount:0,materialTarget:true,batches:[]});
const output=(percent)=>({productId:10,productName:'关节饮',materialCode:'QA-F',baseUnit:'盒',quantityBasis:'BOX',targetQuantity:20000,receivedQuantity:1000,progressPercent:5,productionPercent:percent,unknownQuantityCount:0,provisional:true,movements:[]});
const task=(id,name,targets,percent=18.7)=>({id,name,productId:10,productName:'关节饮',targetQuantity:200000,plannedDate:'2026-10-20',batchNo:`QA-${id}`,status:'IN_PROGRESS',completedQuantity:50000,progressPercent:42.35,issueMovementCount:0,returnMovementCount:0,movements:[],targets,output:output(percent),createdAt:'2026-10-20T00:00:00Z',updatedAt:'2026-10-20T00:00:00Z'});
const tasks=[
  task(1,'关节饮 · 十月生产计划',[material('小绿芯铁',116000,5000),material('关节饮外盒',3800,400,2),material('封口标签',3600,null,3),material('内托',800,400,4),material('装箱贴纸',1600,600,5)]),
  task(2,'小绿芯 · 补充生产任务',[material('外盒',3600,400)],0),
  {...task(3,'三重幼燃粉 · 备料已齐',[material('主料',0)],76.25),progressPercent:100},
  task(4,'关节饮海外版超长名称生产任务用于核对移动端标题换行与进度显示是否清晰完整',[material('海外版关节饮特别规格超长物料名称外盒需要保持完整可读且不能遮挡缺少数量',123456789,5000)],null),
  {...task(5,'包装核对 · 暂无生产数据',[{...material('主料',0),unknownQuantityCount:1}],null),output:undefined},
  {...task(6,'关节饮 · 九月完成任务',[material('主料',0)],100),status:'COMPLETED',progressPercent:100},
  {...task(7,'已取消测试任务',[]),status:'CANCELLED'},
];
const types={'.js':'text/javascript','.css':'text/css','.html':'text/html','.svg':'image/svg+xml'};
createServer(async(req,res)=>{
  const pathname=new URL(req.url,'http://localhost').pathname;
  const json=value=>{res.setHeader('Content-Type','application/json; charset=utf-8');res.end(JSON.stringify(value));};
  if(pathname==='/api/auth/status')return json({authenticated:false,username:''});
  if(pathname==='/api/production-tasks')return json(tasks);
  if(/^\/api\/production-tasks\/\d+$/.test(pathname))return json(tasks.find(t=>t.id===Number(pathname.split('/').at(-1))));
  if(pathname.startsWith('/api/'))return json([]);
  const file=pathname.startsWith('/assets/')?resolve(dist,pathname.slice(1)):resolve(dist,'index.html');
  if(!file.startsWith(dist+sep)&&file!==resolve(dist,'index.html')){res.writeHead(403);return res.end();}
  try{res.setHeader('Content-Type',types[extname(file)]??'application/octet-stream');res.end(await readFile(file));}
  catch{res.writeHead(404);res.end('Not found');}
}).listen(5192,'127.0.0.1',()=>console.log('Task list UI fixture: http://127.0.0.1:5192/production-tasks'));
