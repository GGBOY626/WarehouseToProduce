import { test } from 'node:test';
import assert from 'node:assert/strict';
import { calculateQuantity, cartonCount, quantityError } from '../src/utils/quantity.ts';

const item = (changes = {}) => ({ unitsPerCarton: 1000, fullCartons: 15, tailCartons: 0, looseUnits: 0, quantityUnknown: false, manualTotal: false, ...changes });
test('same material can have 1000 and 1800 per carton without changing its product', () => {
  const rows = [item({productId:1, fullCartons:3}), item({productId:1, unitsPerCarton:1800, fullCartons:5})];
  assert.equal(rows.reduce((n,r) => n + calculateQuantity(r), 0), 12000);
  assert.equal(rows.reduce((n,r) => n + cartonCount(r), 0), 8);
});
test('a real tail carton counts as a box, unboxed units never do', () => {
  const tail=item({tailCartons:1, tailUnitsPerCarton:3});
  const loose=item({looseUnits:3});
  assert.equal(calculateQuantity(tail),15003);
  assert.equal(calculateQuantity(loose),15003);
  assert.equal(cartonCount(tail),16);
  assert.equal(cartonCount(loose),15);
  assert.equal(calculateQuantity({...tail,looseUnits:2}),15005);
});
test('tail-only and loose-only entries work without a full carton specification', () => {
  assert.equal(calculateQuantity(item({unitsPerCarton:null, fullCartons:0, tailCartons:2, tailUnitsPerCarton:3})),6);
  assert.equal(calculateQuantity(item({unitsPerCarton:null, fullCartons:0, looseUnits:7})),7);
});
test('legacy missing specifications remain unknown and editable', () => {
  const old=item({id:1, unitsPerCarton:null, looseUnits:3});
  assert.equal(calculateQuantity(old),undefined);
  assert.equal(quantityError(old),'');
  assert.equal(cartonCount(old),15);
});
test('reject fractions, negative quantities, incomplete tail entries and unsafe totals', () => {
  for(const row of [item({fullCartons:1.5}), item({looseUnits:-2}), item({tailCartons:1}), item({tailUnitsPerCarton:3}), item({unitsPerCarton:2147483647,fullCartons:2147483647})]) {
    assert.notEqual(quantityError(row),'');
  }
});
test('unknown quantity does not erase known packaging; manual total can be validated separately', () => {
  const row=item({quantityUnknown:true,tailCartons:1,tailUnitsPerCarton:3,looseUnits:2});
  assert.equal(calculateQuantity(row),undefined);
  assert.equal(cartonCount(row),16);
  assert.equal(row.looseUnits,2);
  assert.equal(quantityError(item({manualTotal:true,totalUnits:-1})).length>0,true);
});
