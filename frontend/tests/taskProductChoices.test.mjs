import { test } from 'node:test';
import assert from 'node:assert/strict';
import { movementProductChoices } from '../src/utils/taskProductChoices.ts';

test('movement product search includes all active matches, not just task products', () => {
  const catalog = [
    { id: 1, active: true }, { id: 2, active: true },
    { id: 3, active: false }, { id: 4, active: false },
  ];
  assert.deepEqual(movementProductChoices(catalog).map(product => product.id), [1, 2]);
  assert.deepEqual(movementProductChoices(catalog, 4).map(product => product.id), [1, 2, 4]);
});

test('empty movement search shows only ongoing task products and preserves the selected product', () => {
  const catalog = [
    { id: 1, active: true }, { id: 2, active: true },
    { id: 3, active: false }, { id: 4, active: true },
  ];
  assert.deepEqual(movementProductChoices(catalog, undefined, [2, 3]).map(product => product.id), [2]);
  assert.deepEqual(movementProductChoices(catalog, 4, [2]).map(product => product.id), [2, 4]);
  assert.deepEqual(movementProductChoices(catalog, undefined, []).map(product => product.id), []);
});
