import { test } from 'node:test';
import assert from 'node:assert/strict';
import { productPackagingLines } from '../src/utils/productPackaging.ts';

test('shows configured box, unit and pallet levels without guessing', () => {
  assert.deepEqual(productPackagingLines({ baseUnit: '个', boxPackings: [{ boxesPerCarton: 22, unitsPerBox: 30, cartonsPerPallet: 40 }] }),
    ['22 盒/箱 · 30 个/盒 · 660 个/箱 · 40 箱/托']);
});

test('shows only real carton presets and does not duplicate a packed size', () => {
  assert.deepEqual(productPackagingLines({ baseUnit: '条', defaultUnitsPerCarton: 660, cartonSpecs: [400, 660] }),
    ['400 条/箱 · 660 条/箱']);
  assert.deepEqual(productPackagingLines({ baseUnit: '个', cartonSpecs: [660, 800], boxPackings: [{ boxesPerCarton: 22, unitsPerBox: 30 }] }),
    ['22 盒/箱 · 30 个/盒 · 660 个/箱', '800 个/箱']);
});

test('missing or incomplete packing never receives a fabricated unit or quantity', () => {
  assert.deepEqual(productPackagingLines({}), ['箱规未配置']);
  assert.deepEqual(productPackagingLines({ cartonSpecs: [660] }), ['箱规不完整']);
  assert.deepEqual(productPackagingLines({ baseUnit: '个', boxPackings: [{ boxesPerCarton: 22, unitsPerBox: 0 }] }), ['箱规不完整']);
});
