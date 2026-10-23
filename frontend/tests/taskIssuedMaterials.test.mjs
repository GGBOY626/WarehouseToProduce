import { test } from 'node:test';
import assert from 'node:assert/strict';
import { taskIssuedMaterials } from '../src/utils/taskIssuedMaterials.ts';

test('counts only active outgoing cartons and shows issued quantities per task material', () => {
  const task = {
    targets: [
      { productId: 1, productName: '纸盒', materialTarget: true, issuedQuantity: 240, baseUnit: '个', unknownQuantityCount: 0 },
      { productId: 2, productName: '瓶盖', materialTarget: true, issuedQuantity: 80, baseUnit: '个', unknownQuantityCount: 1 },
    ],
    movements: [
      { status: 'ACTIVE', direction: 'WAREHOUSE_TO_PRODUCTION', items: [{ productId: 1, totalCartons: 2 }, { productId: 2, totalCartons: 1 }] },
      { status: 'ACTIVE', direction: 'WAREHOUSE_TO_PRODUCTION', items: [{ productId: 1, totalCartons: 1 }] },
      { status: 'ACTIVE', direction: 'PRODUCTION_TO_WAREHOUSE', items: [{ productId: 1, totalCartons: 5 }] },
      { status: 'VOID', direction: 'WAREHOUSE_TO_PRODUCTION', items: [{ productId: 2, totalCartons: 3 }] },
    ],
  };
  assert.deepEqual(taskIssuedMaterials(task), {
    cartons: 4,
    materials: [
      { productId: 1, name: '纸盒', cartons: 3, quantity: 240, unit: '个', unknownQuantityCount: 0 },
      { productId: 2, name: '瓶盖', cartons: 1, quantity: 80, unit: '个', unknownQuantityCount: 1 },
    ],
  });
});
