import type { ProductionRecipe } from '../types';

const RATE_SCALE = 1_000_000n;

export function recipeRequirements(recipe: ProductionRecipe, outputCount: number) {
  if (!Number.isSafeInteger(outputCount) || outputCount <= 0) throw new Error('计划数量须为正整数。');
  return recipe.items.map(item => {
    if (!Number.isFinite(item.quantityPerOutput) || item.quantityPerOutput <= 0) throw new Error('配方用量无效。');
    const rate = BigInt(Math.round(item.quantityPerOutput * Number(RATE_SCALE)));
    const required = (BigInt(outputCount) * rate + RATE_SCALE - 1n) / RATE_SCALE;
    if (required <= 0n || required > BigInt(Number.MAX_SAFE_INTEGER)) throw new Error('计算出的物料需求超出可保存范围。');
    return { productId: item.productId, targetQuantity: Number(required) };
  });
}
