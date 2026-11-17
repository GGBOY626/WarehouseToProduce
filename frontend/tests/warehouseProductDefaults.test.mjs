import { test } from 'node:test';
import assert from 'node:assert/strict';
import { warehouseProductDefaults } from '../src/utils/warehouseProductDefaults.ts';

test('finished warehouse products map one catalog code to editable batch and SKU defaults', () => {
  assert.deepEqual(warehouseProductDefaults({materialCode:'FG-01',materialCodes:['FG-01'],sku:'SKU-01'},true), {
    materialCodeOptions:[],productionNumberOptions:[],rawMaterialBatchOptions:[],batchOptions:['FG-01'],
    materialCode:'SKU-01',productionNumber:'',rawMaterialBatch:'',batch:'FG-01'
  });
});

test('multiple finished product codes remain unselected for the user to choose', () => {
  const defaults=warehouseProductDefaults({materialCode:'FG-01',materialCodes:['FG-01','FG-02'],sku:'SKU-01'},true);
  assert.deepEqual(defaults.batchOptions,['FG-01','FG-02']);
  assert.equal(defaults.batch,'');
});

test('raw material fields auto-fill only unique values and expose ambiguous choices', () => {
  const defaults=warehouseProductDefaults({materialCode:'MAT-01',materialCodes:['MAT-01','MAT-02'],productionNumbers:['P-01'],materialBatches:['B-01','B-02']},false);
  assert.deepEqual(defaults.materialCodeOptions,['MAT-01','MAT-02']);
  assert.equal(defaults.materialCode,'');
  assert.equal(defaults.productionNumber,'P-01');
  assert.deepEqual(defaults.rawMaterialBatchOptions,['B-01','B-02']);
  assert.equal(defaults.rawMaterialBatch,'');
});
