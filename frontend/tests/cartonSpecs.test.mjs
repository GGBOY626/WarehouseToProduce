import { test } from 'node:test';
import assert from 'node:assert/strict';
import { availableCartonSpecs, initialCartonSpec } from '../src/utils/cartonSpecs.ts';
import { quantityError } from '../src/utils/quantity.ts';

test('multiple presets require a fresh choice even with a legacy default', () => {
  const product = { defaultUnitsPerCarton: 1000, cartonSpecs: [1800, 500, 1000] };
  assert.deepEqual(availableCartonSpecs(product), [500, 1000, 1800]);
  assert.equal(initialCartonSpec(product), undefined);
});

test('one distinct preset auto-selects; an unconfigured product stays empty', () => {
  assert.equal(initialCartonSpec({ cartonSpecs: [500] }), 500);
  assert.equal(initialCartonSpec({ defaultUnitsPerCarton: 1000, cartonSpecs: [1000] }), 1000);
  assert.equal(initialCartonSpec(), undefined);
});

test('full cartons require a choice, while tail-only and loose-only do not', () => {
  const row = { product: { cartonSpecs: [500, 1000, 1800] }, unitsPerCarton: null, fullCartons: 2, tailCartons: 0, looseUnits: 0 };
  assert.match(quantityError(row), /选择本次完整箱规格/);
  assert.match(quantityError({ ...row, quantityUnknown: true }), /选择本次完整箱规格/);
  assert.match(quantityError({ ...row, manualTotal: true, totalUnits: 1000 }), /选择本次完整箱规格/);
  assert.equal(quantityError({ ...row, unitsPerCarton: 1800 }), '');
  assert.equal(quantityError({ ...row, fullCartons: 0, looseUnits: 3 }), '');
  assert.equal(quantityError({ ...row, fullCartons: 0, tailCartons: 1, tailUnitsPerCarton: 3 }), '');
  assert.equal(quantityError({ ...row, id: 9, unitsPerCarton: 700 }), '');
});
