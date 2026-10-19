import test from 'node:test';
import assert from 'node:assert/strict';
import { areaLanes, nextPosition, filterPallets, emptyWarehouseFilters, filterWarehousePallets, warehouseStats, selectWarehouseLocation, changeWarehouseScope } from '../src/utils/warehouse.ts';
const pallets=[
  {id:1,areaId:1,lane:'UPPER',position:1,code:'TP-1',location:'A 排 / 上层 / 01 号',items:[{productName:'外盒',materialCode:'BOX',batch:'22334',quantity:2000}]},
  {id:2,areaId:1,lane:'UPPER',position:3,code:'TP-2',location:'A 排 / 上层 / 03 号',items:[{productName:'外盒',materialCode:'BOX',batch:'22335',quantity:null}]},
  {id:3,areaId:2,lane:'LEFT_2',position:1,code:'TP-3',location:'过道 1 / 左二 / 01 号',items:[{productName:'主料',materialCode:'RAW',batch:'22334',quantity:500}]}
];
test('material type filters mixed pallets before keyword and pending checks without changing source items',()=>{
  const mixed=[{...pallets[0],kind:'RACK',items:[{productName:'成品饮料',finished:true,quantity:null},{productName:'外盒',finished:false,quantity:100}]}];
  const scope={...emptyWarehouseFilters(),finished:false};
  assert.deepEqual(filterWarehousePallets(mixed,[],scope)[0].items.map(i=>i.productName),['外盒']);
  assert.equal(filterWarehousePallets(mixed,[],{...scope,query:'饮料'}).length,0);
  assert.equal(filterWarehousePallets(mixed,[],{...scope,unknownOnly:true}).length,0);
  assert.deepEqual(filterWarehousePallets(mixed,[],{...scope,finished:true})[0].items.map(i=>i.productName),['成品饮料']);
  assert.equal(mixed[0].items.length,2);
});
test('rack levels and aisle widths only expose valid locations',()=>{
  assert.deepEqual(areaLanes({kind:'RACK',lanes:3}),['UPPER','MIDDLE','LOWER']);
  assert.deepEqual(areaLanes({kind:'AISLE',lanes:2}),['LEFT_1','RIGHT_1']);
  assert.deepEqual(areaLanes({kind:'AISLE',lanes:4}),['LEFT_1','LEFT_2','RIGHT_1','RIGHT_2']);
  assert.deepEqual(areaLanes(),[]);
});

const areas=[{id:1,name:'A',kind:'RACK',lanes:3},{id:2,name:'过道 1',kind:'AISLE',rackRow:'A',lanes:4},{id:3,name:'B',kind:'RACK',lanes:3},{id:4,name:'独立过道',kind:'AISLE',lanes:2}];
const inventory=[
  ...pallets.map(p=>({...p,kind:p.areaId===1?'RACK':'AISLE'})),
  {id:4,areaId:3,kind:'RACK',lane:'LOWER',position:1,code:'TP-4',location:'B 排 / 下层 / 01 号',items:[{productName:'主料',rawMaterialBatch:'RAW-26',quantity:800},{productName:'备用盒',quantity:null}]},
  {id:5,areaId:4,kind:'AISLE',lane:'LEFT_1',position:1,code:'TP-5',location:'独立过道 / 左一 / 01 号',items:[{productName:'辅料',quantity:50}]}
];
test('warehouse statistics count pallets, not items, and pending overlaps locations',()=>{
  assert.deepEqual(warehouseStats(inventory),{total:5,rack:3,aisle:2,pending:2});
  assert.deepEqual(warehouseStats([]),{total:0,rack:0,aisle:0,pending:0});
  assert.equal(warehouseStats([{kind:'RACK',items:[{quantity:0},{quantity:12}]}]).pending,0);
  assert.equal(inventory[3].items[1].quantity,null);
});
test('row includes its rack and linked aisles, never unlinked aisles',()=>{
  const ids=filters=>filterWarehousePallets(inventory,areas,{...emptyWarehouseFilters(),...filters}).map(p=>p.id);
  assert.deepEqual(ids({row:'A'}),[1,2,3]);
  assert.deepEqual(ids({row:'A',kind:'AISLE'}),[3]);
  assert.deepEqual(ids({kind:'AISLE'}),[3,5]);
  assert.deepEqual(ids({row:'B',kind:'AISLE'}),[]);
  assert.deepEqual(ids({row:'B',unknownOnly:true,query:'raw-26'}),[4]);
  assert.deepEqual(ids({query:'备用盒',unknownOnly:true}),[4]);
});
test('board selection synchronizes controls and preserves search/pending filters',()=>{
  const filters=selectWarehouseLocation({...emptyWarehouseFilters(),query:'22335',unknownOnly:true,kind:'AISLE',row:'B'},areas[0],'UPPER');
  assert.deepEqual(filters,{query:'22335',unknownOnly:true,hideFinished:false,kind:'RACK',row:'A',areaId:1,lane:'UPPER'});
  assert.deepEqual(filterWarehousePallets(inventory,areas,filters).map(p=>p.id),[2]);
  assert.equal(filterWarehousePallets(inventory,areas,{...filters,lane:'LOWER'}).length,0);
  const unlinked=selectWarehouseLocation(filters,areas[3],'LEFT_1');
  assert.equal(unlinked.row,'');
  assert.equal(unlinked.kind,'AISLE');
});
test('changing broader scope drops stale exact location; clearing restores all pallets',()=>{
  const selected=selectWarehouseLocation(emptyWarehouseFilters(),areas[0],'UPPER');
  const aisle=changeWarehouseScope(selected,{kind:'AISLE'});
  assert.equal(aisle.areaId,undefined);assert.equal(aisle.lane,'');assert.equal(aisle.row,'A');
  assert.deepEqual(filterWarehousePallets(inventory,areas,aisle).map(p=>p.id),[3]);
  assert.deepEqual(filterWarehousePallets(inventory,areas,changeWarehouseScope(selected,{row:'B'})).map(p=>p.id),[4]);
  assert.equal(filterWarehousePallets(inventory,areas,emptyWarehouseFilters()).length,inventory.length);
});
test('a moved pallet is counted once and disappears from its former location',()=>{
  const moved=inventory.map(p=>p.id===1?{...p,areaId:2,kind:'AISLE',lane:'RIGHT_2',position:1}:p);
  assert.deepEqual(warehouseStats(moved),{total:5,rack:2,aisle:3,pending:2});
  assert.deepEqual(filterWarehousePallets(moved,areas,selectWarehouseLocation(emptyWarehouseFilters(),areas[0],'UPPER')).map(p=>p.id),[2]);
  assert.deepEqual(filterWarehousePallets(moved,areas,selectWarehouseLocation(emptyWarehouseFilters(),areas[1],'RIGHT_2')).map(p=>p.id),[1]);
});
test('continuous entry suggests the first unused position in exactly one lane',()=>{
  const physical=pallets.map(p=>p.areaId===1?{...p,position:p.position+4}:p);
  assert.equal(nextPosition(physical,1,'UPPER'),6);
  assert.equal(nextPosition(physical,1,'LOWER'),5);
  assert.equal(nextPosition(physical,2,'LEFT_2',1),2);
});
test('batch, product, location and unknown filters combine without mixing locations',()=>{
  assert.deepEqual(filterPallets(pallets,'22334').map(p=>p.id),[1,3]);
  assert.deepEqual(filterPallets(pallets,' box ',1,'UPPER',true).map(p=>p.id),[2]);
  assert.deepEqual(filterPallets(pallets,'左二').map(p=>p.id),[3]);
  assert.deepEqual(filterPallets(pallets,'TP-1').map(p=>p.id),[1]);
  assert.equal(filterPallets(pallets,'22334',1,'LOWER').length,0);
});
test('raw material batch is searchable independently of batch and optional catalog fields',()=>{
  const manual={id:4,areaId:2,lane:'LEFT_2',position:2,code:'TP-4',location:'A 排旁过道 / 左二 / 02 号',items:[{productId:null,productName:'备用外盒',rawMaterialBatch:'RAW-2026',batch:null,materialCode:null,quantity:null}]};
  assert.deepEqual(filterPallets([...pallets,manual],'raw-2026').map(p=>p.id),[4]);
  assert.deepEqual(filterPallets([...pallets,manual],'备用外盒').map(p=>p.id),[4]);
  assert.deepEqual(filterPallets([...pallets,manual],'RAW-2026',2,'LEFT_2',true).map(p=>p.id),[4]);
});
