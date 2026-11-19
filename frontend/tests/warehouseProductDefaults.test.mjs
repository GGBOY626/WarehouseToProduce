import { test } from 'node:test';
import assert from 'node:assert/strict';
import { warehouseProductDefaults } from '../src/utils/warehouseProductDefaults.ts';

test('finished warehouse products map production numbers to BATCH and SKU to the SKU field', () => {
  assert.deepEqual(warehouseProductDefaults({materialCode:'SKU-01',materialCodes:['SKU-01'],sku:'SKU-01',productionNumbers:['BATCH-01']},true), {
    materialCodeOptions:['SKU-01'],productionNumberOptions:[],rawMaterialBatchOptions:[],batchOptions:['BATCH-01'],
    materialCode:'SKU-01',productionNumber:'',rawMaterialBatch:'',batch:'BATCH-01'
  });
});

test('multiple finished BATCH values and SKUs remain unselected for the user to choose', () => {
  const defaults=warehouseProductDefaults({materialCode:'SKU-01',materialCodes:['SKU-01','SKU-02'],sku:'SKU-01',productionNumbers:['BATCH-01','BATCH-02']},true);
  assert.deepEqual(defaults.batchOptions,['BATCH-01','BATCH-02']);
  assert.equal(defaults.batch,'');
  assert.deepEqual(defaults.materialCodeOptions,['SKU-01','SKU-02']);
  assert.equal(defaults.materialCode,'');
});

test('raw material fields auto-fill only unique values and expose ambiguous choices', () => {
  const defaults=warehouseProductDefaults({materialCode:'MAT-01',materialCodes:['MAT-01','MAT-02'],productionNumbers:['P-01'],materialBatches:['B-01','B-02']},false);
  assert.deepEqual(defaults.materialCodeOptions,['MAT-01','MAT-02']);
  assert.equal(defaults.materialCode,'');
  assert.equal(defaults.productionNumber,'P-01');
  assert.deepEqual(defaults.rawMaterialBatchOptions,['B-01','B-02']);
  assert.equal(defaults.rawMaterialBatch,'');
});
