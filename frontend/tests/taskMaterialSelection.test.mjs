import { test } from 'node:test';
import assert from 'node:assert/strict';
import { materialOptions, applyMaterialSelection } from '../src/utils/taskMaterialSelection.ts';

const product = { id: 1, materialCode: 'C1', materialCodes: ['C1', 'C2'], materialBatches: ['B1', 'B2'], productionNumbers: ['N1', 'N2'] };
const selection = { productId: 1, materialCodes: ['C2'], materialBatches: ['B2'], productionNumbers: ['N2'] };

test('task single selections override catalog defaults for issues and finished receipts', () => {
  const item = { product, batchNo: 'C1', materialBatch: '', productionNumber: '' };
  applyMaterialSelection(item, selection);
  assert.deepEqual([item.batchNo, item.materialBatch, item.productionNumber], ['C2', 'B2', 'N2']);
  assert.deepEqual(materialOptions(product, selection), { materialCodes: ['C2'], materialBatches: ['B2'], productionNumbers: ['N2'] });
  // A refreshed task response must not erase the autofill or reopen catalog choices.
  applyMaterialSelection(item, structuredClone(selection));
  assert.equal(item.materialBatch, 'B2');
  assert.equal(item.productionNumber, 'N2');
});

test('multiple task selections keep valid choices and clear values from another task', () => {
  const choices = { productId: 1, materialCodes: ['C2', 'C3'], materialBatches: ['B2', 'B3'], productionNumbers: ['N2', 'N3'] };
  const item = { product, batchNo: 'C1', materialBatch: 'B1', productionNumber: 'N1' };
  applyMaterialSelection(item, choices);
  assert.deepEqual([item.batchNo, item.materialBatch, item.productionNumber], ['', '', '']);
  Object.assign(item, { batchNo: 'C3', materialBatch: 'B3', productionNumber: 'N3' });
  applyMaterialSelection(item, choices);
  assert.deepEqual([item.batchNo, item.materialBatch, item.productionNumber], ['C3', 'B3', 'N3']);
});

test('unrestricted legacy tasks retain catalog choices and saved movements keep their actual values', () => {
  assert.deepEqual(materialOptions(product, { productId: 1, materialCodes: [], materialBatches: [], productionNumbers: [] }), materialOptions(product));
  const item = { id: 10, product, batchNo: 'historical-code', materialBatch: 'historical-batch', productionNumber: 'historical-number' };
  applyMaterialSelection(item, selection);
  assert.deepEqual([item.batchNo, item.materialBatch, item.productionNumber], ['historical-code', 'historical-batch', 'historical-number']);
});

test('single catalog values still autofill without any task', () => {
  const item = { product: { materialCode: 'C1', materialBatch: 'B1', productionNumbers: ['N1'] } };
  applyMaterialSelection(item);
  assert.deepEqual([item.batchNo, item.materialBatch, item.productionNumber], ['C1', 'B1', 'N1']);
});
