import test from 'node:test';
import assert from 'node:assert/strict';
import { initialBoxPacking, applyBoxPacking, syncBoxQuantity, packingError } from '../src/utils/boxPacking.ts';
import { initialCartonSpec } from '../src/utils/cartonSpecs.ts';
import { calculateQuantity, quantityError } from '../src/utils/quantity.ts';
const packing={boxesPerCarton:28,unitsPerBox:30};
const row=()=>({product:{baseUnit:'条'},fullCartons:2,tailCartons:1,looseUnits:0,quantityUnknown:false,manualTotal:false});
test('28 boxes of 30 and a tail of 12 boxes calculate without manual multiplication',()=>{
  const item=row();applyBoxPacking(item,packing);item.tailBoxesPerCarton=12;syncBoxQuantity(item);
  assert.equal(item.unitsPerCarton,840);assert.equal(item.tailUnitsPerCarton,360);
  assert.equal(calculateQuantity(item),2040);assert.equal(quantityError(item),'');
  item.unitsPerBox=20;syncBoxQuantity(item);
  assert.equal(item.unitsPerCarton,560);assert.equal(item.tailUnitsPerCarton,240);
  item.fullCartons=0;assert.equal(calculateQuantity(item),240);
  applyBoxPacking(item,{boxesPerCarton:24,unitsPerBox:10});syncBoxQuantity(item);
  assert.equal(item.tailBoxesPerCarton,12);assert.equal(item.tailUnitsPerCarton,120);
});
test('one preset is automatic, multiple decompositions require a choice even with the same total',()=>{
  assert.deepEqual(initialBoxPacking({boxPackings:[packing],defaultUnitsPerCarton:840}),packing);
  const multiple={boxPackings:[packing,{boxesPerCarton:14,unitsPerBox:60}]};
  assert.equal(initialBoxPacking(multiple),undefined);assert.equal(initialCartonSpec(multiple),undefined);
  assert.equal(initialBoxPacking({boxPackings:[packing],cartonSpecs:[900]}),undefined);
  assert.equal(initialBoxPacking({defaultUnitsPerCarton:840}),undefined);
});
test('legacy quantities are not guessed and incomplete packaging cannot be submitted',()=>{
  const item={...row(),unitsPerCarton:840,tailUnitsPerCarton:360};syncBoxQuantity(item);
  assert.equal(calculateQuantity(item),2040);
  applyBoxPacking(item,packing);assert.equal(item.tailBoxesPerCarton,12);
  item.boxesPerCarton=0;syncBoxQuantity(item);assert.notEqual(quantityError(item),'');assert.equal(calculateQuantity(item),undefined);
  item.boxesPerCarton=28;item.tailBoxesPerCarton=12.5;syncBoxQuantity(item);assert.notEqual(quantityError(item),'');
});
test('unknown quantities, loose units and no tail are preserved correctly',()=>{
  const item=row();applyBoxPacking(item,packing);item.tailBoxesPerCarton=12;item.looseUnits=5;syncBoxQuantity(item);
  assert.equal(calculateQuantity(item),2045);
  item.quantityUnknown=true;assert.equal(calculateQuantity(item),undefined);
  item.tailCartons=0;syncBoxQuantity(item);assert.equal(item.tailUnitsPerCarton,null);assert.equal(item.tailBoxesPerCarton,null);
});
test('box-based units and integer overflow are validated',()=>{
  assert.notEqual(packingError(packing,'盒'),'');
  assert.equal(packingError({boxesPerCarton:28,unitsPerBox:1},'盒'),'');
  for(const p of [{boxesPerCarton:28,unitsPerBox:0},{boxesPerCarton:1.5,unitsPerBox:30},{boxesPerCarton:2147483647,unitsPerBox:30}])assert.notEqual(packingError(p),'');
});
