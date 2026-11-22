import test from 'node:test';
import assert from 'node:assert/strict';
import { recipeRequirements } from '../src/utils/productionRecipe.ts';

const recipe = {
  items: [
    { productId: 11, quantityPerOutput: 60 },
    { productId: 12, quantityPerOutput: 1 },
    { productId: 13, quantityPerOutput: 0.25 },
  ],
};

test('scales recipe components by planned bags or boxes and rounds fractional material up', () => {
  assert.deepEqual(recipeRequirements(recipe, 3), [
    { productId: 11, targetQuantity: 180 },
    { productId: 12, targetQuantity: 3 },
    { productId: 13, targetQuantity: 1 },
  ]);
  assert.deepEqual(recipeRequirements(recipe, 8)[2], { productId: 13, targetQuantity: 2 });
});

test('rejects invalid planned quantities and material totals outside safe integer range', () => {
  assert.throws(() => recipeRequirements(recipe, 0));
  assert.throws(() => recipeRequirements({ items: [{ productId: 1, quantityPerOutput: 2 } ] }, Number.MAX_SAFE_INTEGER));
});
