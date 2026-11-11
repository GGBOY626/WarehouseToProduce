import type { ItemInput, Product, TaskMaterialSelection } from '../types';

export function materialOptions(product?: Product, selection?: TaskMaterialSelection) {
  return {
    materialBatches: selection?.materialBatches?.length ? selection.materialBatches : product?.materialBatches?.length ? product.materialBatches : product?.materialBatch ? [product.materialBatch] : [],
    materialCodes: selection?.materialCodes?.length ? selection.materialCodes : product?.materialCodes?.length ? product.materialCodes : product?.materialCode ? [product.materialCode] : [],
    productionNumbers: selection?.productionNumbers?.length ? selection.productionNumbers : product?.productionNumbers || [],
  };
}

export function applyMaterialSelection(item: ItemInput, selection?: TaskMaterialSelection) {
  if (item.id) return;
  const options = materialOptions(item.product, selection);
  for (const [field, key] of [['materialBatch', 'materialBatches'], ['batchNo', 'materialCodes'], ['productionNumber', 'productionNumbers']] as const) {
    const choices = options[key];
    if (choices.length === 1) item[field] = choices[0];
    else if (selection?.[key]?.length && !choices.includes(item[field] || '')) item[field] = '';
  }
}
