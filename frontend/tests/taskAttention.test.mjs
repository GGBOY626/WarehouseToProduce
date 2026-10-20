import {test} from 'node:test';
import assert from 'node:assert/strict';
import {activeTask,taskAlerts,attentionFirst,taskWarnings} from '../src/utils/taskAttention.ts';
const target={materialTarget:true,shortageQuantity:0,unknownQuantityCount:0};
const normal={id:1,status:'IN_PROGRESS',targets:[target]};

test('structured warning presentation preserves all existing attention conditions and carton rounding',()=>{
 const task={...normal,targets:[{...target,productName:'外盒',baseUnit:'个',shortageQuantity:3800,shortageCartonSize:400,unknownQuantityCount:1},{...target,materialTarget:false}],output:{producedQuantity:101,netIssuedQuantity:100}};
 assert.deepEqual(taskWarnings(task),['有数量待确认','备料配置待核对','回仓数量待核对']);
 assert.equal(taskAlerts(task),`外盒 缺 ${(3800).toLocaleString()} 个，还需 10 箱（按 400 个/箱） · 有数量待确认 · 备料配置待核对 · 回仓数量待核对`);
 assert.deepEqual(taskWarnings(normal),[]);
});
test('attention identifies shortages and uncertain quantities without adding different units',()=>{
 const task={...normal,targets:[{...target,productName:'主料',shortageQuantity:20,baseUnit:'条'},{...target,productName:'外盒',shortageQuantity:50,baseUnit:'个'}]};
 assert.equal(taskAlerts(task),'主料 缺 20 条 · 外盒 缺 50 个');
 assert.equal(taskAlerts({...normal,output:{unknownQuantityCount:1}}),'有数量待确认');
 assert.equal(taskAlerts(normal),'');
});
test('attention sorting is stable and does not mutate tasks or change progress',()=>{
 const missing={...normal,id:2,progressPercent:25,targets:[{...target,shortageQuantity:5}]};
 const later={...normal,id:3};const original=[normal,missing,later];
 assert.deepEqual(attentionFirst(original).map(t=>t.id),[2,1,3]);
 assert.deepEqual(original.map(t=>t.id),[1,2,3]);assert.equal(missing.progressPercent,25);
});
test('overview active tasks exclude completed and cancelled tasks; quantity warning uses existing values',()=>{
 assert.equal(activeTask({...normal,status:'PREPARING'}),true);
 assert.equal(activeTask({...normal,status:'COMPLETED'}),false);
 assert.equal(activeTask({...normal,status:'CANCELLED'}),false);
 assert.equal(taskAlerts({...normal,output:{producedQuantity:101,netIssuedQuantity:100}}),'回仓数量待核对');
});
