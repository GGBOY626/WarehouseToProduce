import { test } from 'node:test';
import assert from 'node:assert/strict';
import { taskCartonUnits, unitsForBoxes, boxesForUnits, fullCartonsForPalletSelection, tailCartonsOnOpen } from '../src/utils/taskPackaging.ts';
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
test('a selected whole-pallet count adds the configured cartons and tail cartons', () => {
  const fullCartons=fullCartonsForPalletSelection(4,30,3);
  assert.equal(fullCartons,94);
  assert.equal(fullCartonsForPalletSelection(4,30,0),4);
  assert.equal(fullCartonsForPalletSelection(4,null,3),4);
  assert.equal(calculateQuantity({unitsPerCarton:28,fullCartons,tailCartons:1,tailUnitsPerCarton:3,looseUnits:0}),2635);
});
test('opening a new receipt tail section starts at one without restoring a user-cleared count', () => {
  assert.equal(tailCartonsOnOpen(0,false,true),1);
  assert.equal(tailCartonsOnOpen(0,true,true),0);
  assert.equal(tailCartonsOnOpen(0,false,false),0);
  assert.equal(tailCartonsOnOpen(2,false,true),2);
});
