import { test } from 'node:test';
import assert from 'node:assert/strict';
import { taskProductChoices } from '../src/utils/taskProductChoices.ts';

test('blank material choices use only linked materials from ongoing tasks', () => {
  const catalog = [1, 2, 3, 4].map(id => ({ id }));
  const tasks = [
    { status: 'PREPARING', targets: [{ productId: 1, materialTarget: true }, { productId: 2, materialTarget: false }], output: { productId: 2 } },
    { status: 'IN_PROGRESS', targets: [{ productId: 3, materialTarget: true }], output: { productId: 4 } },
    { status: 'COMPLETED', targets: [{ productId: 4, materialTarget: true }], output: { productId: 1 } },
  ];
  assert.deepEqual(taskProductChoices(catalog, tasks, true).map(product => product.id), [1, 3]);
  assert.deepEqual(taskProductChoices(catalog, tasks, false).map(product => product.id), [2, 4]);
  assert.deepEqual(taskProductChoices(catalog, tasks, true, 4).map(product => product.id), [1, 3, 4]);
});
