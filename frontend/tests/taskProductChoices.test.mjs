import { test } from 'node:test';
import assert from 'node:assert/strict';
import { taskProductChoices } from '../src/utils/taskProductChoices.ts';

test('product choices are restricted to products used by active task options', () => {
  const catalog = [1, 2, 3, 4].map(id => ({ id }));
  assert.deepEqual(taskProductChoices(catalog, [1, 3]).map(product => product.id), [1, 3]);
  assert.deepEqual(taskProductChoices(catalog, [1, 3], 4).map(product => product.id), [1, 3, 4]);
});
