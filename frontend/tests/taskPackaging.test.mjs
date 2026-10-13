import { test } from 'node:test';
import assert from 'node:assert/strict';
import { taskCartonUnits, unitsForBoxes, boxesForUnits } from '../src/utils/taskPackaging.ts';
import { calculateQuantity, cartonCount } from '../src/utils/quantity.ts';

test('28 boxes of 30 units and a three-box tail preserve actual units and cartons', () => {
  const packing = {boxesPerCarton:28,unitsPerBox:30,quantityBasis:'UNIT'};
  assert.equal(taskCartonUnits(packing),840);
  assert.equal(unitsForBoxes(packing,3),90);
  const row = {unitsPerCarton:taskCartonUnits(packing),fullCartons:10,tailCartons:1,tailUnitsPerCarton:unitsForBoxes(packing,3),looseUnits:2};
  assert.equal(calculateQuantity(row),8492);
  assert.equal(cartonCount(row),11);
  assert.equal(boxesForUnits(packing,row.tailUnitsPerCarton),3);
});
test('box-based inventory records boxes without multiplying the stock quantity twice', () => {
  const packing = {boxesPerCarton:28,unitsPerBox:30,quantityBasis:'BOX'};
  assert.equal(taskCartonUnits(packing),28);
  assert.equal(unitsForBoxes(packing,3),3);
  assert.equal(boxesForUnits(packing,3),3);
  assert.equal(calculateQuantity({unitsPerCarton:28,fullCartons:0,tailCartons:1,tailUnitsPerCarton:3,looseUnits:0}),3);
});
