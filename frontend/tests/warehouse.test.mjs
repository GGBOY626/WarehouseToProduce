import test from 'node:test';
import assert from 'node:assert/strict';
import { areaLanes, nextPosition, filterPallets } from '../src/utils/warehouse.ts';
const pallets=[
  {id:1,areaId:1,lane:'UPPER',position:1,code:'TP-1',location:'A 排 / 上层 / 01 号',items:[{productName:'外盒',materialCode:'BOX',batch:'22334',quantity:2000}]},
  {id:2,areaId:1,lane:'UPPER',position:3,code:'TP-2',location:'A 排 / 上层 / 03 号',items:[{productName:'外盒',materialCode:'BOX',batch:'22335',quantity:null}]},
  {id:3,areaId:2,lane:'LEFT_2',position:1,code:'TP-3',location:'过道 1 / 左二 / 01 号',items:[{productName:'主料',materialCode:'RAW',batch:'22334',quantity:500}]}
];
test('rack levels and aisle widths only expose valid locations',()=>{
  assert.deepEqual(areaLanes({kind:'RACK',lanes:3}),['UPPER','MIDDLE','LOWER']);
  assert.deepEqual(areaLanes({kind:'AISLE',lanes:2}),['LEFT_1','RIGHT_1']);
  assert.deepEqual(areaLanes({kind:'AISLE',lanes:4}),['LEFT_1','LEFT_2','RIGHT_1','RIGHT_2']);
  assert.deepEqual(areaLanes(),[]);
});
test('continuous entry suggests the first unused position in exactly one lane',()=>{
  assert.equal(nextPosition(pallets,1,'UPPER'),2);
  assert.equal(nextPosition(pallets,1,'LOWER'),1);
  assert.equal(nextPosition(pallets,2,'LEFT_2'),2);
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
