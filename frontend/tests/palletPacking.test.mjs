import test from 'node:test';
import assert from 'node:assert/strict';
import { palletPacking } from '../src/utils/palletPacking.ts';
import { fullCartonsForPalletSelection } from '../src/utils/taskPackaging.ts';
import { calculateQuantity, quantityError } from '../src/utils/quantity.ts';

const packings = [
  { boxesPerCarton: 20, unitsPerBox: 30, cartonsPerPallet: 42 },
  { boxesPerCarton: 22, unitsPerBox: 30, cartonsPerPallet: 42 },
];
const item = { product: { boxPackings: packings } };

test('N60784PSN supports 42 cartons per pallet before selecting either box size', () => {
  assert.deepEqual(palletPacking(item), { cartonsPerPallet: 42, requiresPackingSelection: false });
  for (const packing of packings) {
    const count = palletPacking({ ...item, ...packing }).cartonsPerPallet;
    const row = { ...item, ...packing, unitsPerCarton: packing.boxesPerCarton * packing.unitsPerBox,
      fullCartons: fullCartonsForPalletSelection(0, count, 2), tailCartons: 0, looseUnits: 0 };
    assert.equal(row.fullCartons, 84);
    assert.equal(calculateQuantity(row), 84 * packing.boxesPerCarton * packing.unitsPerBox);
  }
  assert.match(quantityError({ ...item, fullCartons: 42, tailCartons: 0, looseUnits: 0 }), /请选择本次完整箱规格/);
});

test('different or partly missing pallet sizes require an explicit packing choice', () => {
  for (const cartonsPerPallet of [40, null]) {
    const product = { boxPackings: [packings[0], { ...packings[1], cartonsPerPallet }] };
    assert.deepEqual(palletPacking({ product }), { cartonsPerPallet: undefined, requiresPackingSelection: true });
    assert.equal(palletPacking({ product, ...packings[0] }).cartonsPerPallet, 42);
    assert.equal(palletPacking({ product, ...packings[1] }).cartonsPerPallet, cartonsPerPallet ?? undefined);
  }
});

test('task pallet configuration takes priority and its factors select the catalog fallback', () => {
  assert.equal(palletPacking(item, { outputCartonsPerPallet: 36 }).cartonsPerPallet, 36);
  const product = { boxPackings: [packings[0], { ...packings[1], cartonsPerPallet: 40 }] };
  assert.equal(palletPacking({ product }, { outputBoxesPerCarton: 22, outputUnitsPerBox: 30 }).cartonsPerPallet, 40);
});

test('legacy carton sizes and custom factors never inherit an unrelated pallet size', () => {
  assert.equal(palletPacking({ ...item, unitsPerCarton: 600 }).cartonsPerPallet, 42);
  assert.equal(palletPacking({ ...item, unitsPerCarton: 900 }).cartonsPerPallet, undefined);
  assert.equal(palletPacking({ ...item, boxesPerCarton: 10, unitsPerBox: 30 }).cartonsPerPallet, undefined);
  assert.equal(palletPacking({ product: { ...item.product, cartonSpecs: [900] } }).requiresPackingSelection, true);
  assert.deepEqual(palletPacking({ product: {} }), { cartonsPerPallet: undefined, requiresPackingSelection: false });
  for (const count of [0, -1, 1.5, NaN]) assert.equal(palletPacking({ product: { boxPackings: [{ ...packings[0], cartonsPerPallet: count }] } }).cartonsPerPallet, undefined);
});
