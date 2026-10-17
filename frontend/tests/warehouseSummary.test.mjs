import test from 'node:test';
import assert from 'node:assert/strict';
import { summarizeWarehouse, amountLabel } from '../src/utils/warehouseSummary.ts';
import { emptyWarehouseFilters, selectWarehouseLocation } from '../src/utils/warehouse.ts';
const areas=[{id:1,name:'A',kind:'RACK'},{id:2,name:'B',kind:'RACK'},{id:3,name:'收货过道',kind:'AISLE',rackRow:'A'}];
const item=(fields={})=>({productId:1,productName:'关节饮',materialCode:'M1',batch:'22344',rawMaterialBatch:'RAW-1',quantity:12000,cartons:99,unit:'件',...fields});
const pallet=(id,items,areaId=1)=>({id,code:`TP-${id}`,areaId,kind:areaId===3?'AISLE':'RACK',lane:areaId===3?'LEFT_1':'UPPER',position:id,location:`${areaId===3?'收货过道 / 左一':areas[areaId-1].name+' 排 / 上层'} / ${id} 号`,items});
const summary=(rows,filters={})=>summarizeWarehouse(rows,areas,{...emptyWarehouseFilters(),...filters});
test('same batch sums across locations once, preserving distinct rows and batches',()=>{
  const a=pallet(1,[item(),item({batch:'22345',quantity:50000})]);
  const b=pallet(2,[item({quantity:8000}),item({productId:2,productName:'三重幼燃粉',materialCode:'M2',batch:'22342',quantity:150000})],2);
  const result=summary([a,b,a]);
  const m=result.find(m=>m.name==='关节饮');
  assert.equal(m.quantities['件'],70000n);
  assert.deepEqual(m.batches.map(b=>b.quantities['件']),[20000n,50000n]);
  assert.deepEqual(m.batches[0].locations.map(l=>l.location),[a.location,b.location]);
  assert.equal(summary([pallet(1,[item(),item()])])[0].quantities['件'],24000n);
});
test('pending, missing unit, mixed units, cartons and actual zero stay distinct',()=>{
  const m=summary([pallet(1,[item({quantity:null}),item({quantity:0}),item({unit:'kg',quantity:5}),item({unit:null,quantity:200})])])[0];
  assert.deepEqual({...m.quantities},{'件':0n,kg:5n}); assert.equal(m.pending,1); assert.equal(m.unitUnknown,1);
  assert.match(amountLabel(m),/0 件 \/ 5 kg/);
  assert.equal(amountLabel(summary([pallet(2,[item({quantity:null,cartons:100})])])[0]),'数量待确认');
});
test('missing batches are individual unknown records, raw batch never substitutes for batch',()=>{
  const m=summary([pallet(1,[item({batch:null}),item({batch:' '})]),pallet(2,[item({batch:null})])])[0];
  assert.equal(m.batches.length,3); assert.equal(new Set(m.batches.map(b=>b.key)).size,3);
  assert.ok(m.batches.every(b=>b.missing&&b.label==='批号未录入'));
  assert.equal(m.batches[0].locations[0].rawBatches[0],'RAW-1');
});
test('identity uses exact name and catalog ID, only unambiguous manual codes bridge',()=>{
  const rows=[pallet(1,[item(),item({productId:null}),item({productId:2}),item({productName:'关节饮 Plus'}),item({productId:null,materialCode:'M9'}),item({productId:null,materialCode:null})])];
  assert.equal(summary(rows).length,6);
  const joined=summary([pallet(1,[item(),item({productId:null})])]);
  assert.equal(joined.length,1);assert.equal(joined[0].quantities['件'],24000n);
  assert.equal(summary([pallet(1,[item({productId:null}),item({productId:null,batch:'B2'})])]).length,1);
});
test('search and pending filter match individual items, location scope matches the board',()=>{
  const rows=[pallet(1,[item(),item({productId:2,productName:'外盒',materialCode:'BOX',batch:'B2',quantity:null})]),pallet(2,[item({quantity:8000})],2),pallet(3,[item({quantity:500})],3)];
  assert.equal(summary(rows,{query:'B2'})[0].name,'外盒');
  assert.equal(summary(rows,{query:'关节饮',unknownOnly:true}).length,0);
  assert.equal(summary(rows,{unknownOnly:true})[0].pending,1);
  const selected=selectWarehouseLocation(emptyWarehouseFilters(),areas[0],'UPPER');
  assert.equal(summary(rows,{...selected,query:'22344'})[0].quantities['件'],12000n);
  assert.equal(summary(rows,{row:'A',query:'22344'})[0].quantities['件'],12500n);
  assert.equal(summary(rows,{kind:'AISLE'})[0].batches[0].locations[0].location,rows[2].location);
  assert.equal(summary(rows,{query:'RAW-1'}).length,2);
});
test('large totals remain exact beyond the safe number range',()=>{
  const m=summary([pallet(1,[item({quantity:Number.MAX_SAFE_INTEGER}),item({quantity:Number.MAX_SAFE_INTEGER})])])[0];
  assert.equal(m.quantities['件'],18014398509481982n);
});
