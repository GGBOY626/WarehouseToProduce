import type { ItemInput } from '../types';
import { packingError } from './boxPacking.ts';
import { availableCartonSpecs } from './cartonSpecs.ts';

export function cartonCount(item: Pick<ItemInput, 'fullCartons' | 'tailCartons'>) {
  return Number(item.fullCartons || 0) + Number(item.tailCartons || 0);
}
export function calculateQuantity(item: ItemInput): number | undefined {
  if (item.quantityUnknown) return undefined;
  const full = Number(item.fullCartons || 0), tail = Number(item.tailCartons || 0);
  if (full > 0 && !item.unitsPerCarton) return undefined;
  if (tail > 0 && !item.tailUnitsPerCarton) return undefined;
  return full * Number(item.unitsPerCarton || 0) + tail * Number(item.tailUnitsPerCarton || 0) + Number(item.looseUnits || 0);
}
export function quantityError(item: ItemInput): string {
  if (item.boxesPerCarton != null || item.unitsPerBox != null) {
    const error=packingError({boxesPerCarton:item.boxesPerCarton??undefined,unitsPerBox:item.unitsPerBox??undefined},item.product?.baseUnit);
    if(error)return error;
    if(item.tailCartons>0&&(!Number.isInteger(item.tailBoxesPerCarton)||Number(item.tailBoxesPerCarton)<=0))return '请填写每个尾箱实际盒数。';
  }
  const counts = [item.fullCartons || 0, item.tailCartons || 0, item.looseUnits || 0];
  if (counts.some(n => !Number.isSafeInteger(n) || n < 0)) return '箱数和散装数量必须为非负整数。';
  if (cartonCount(item) > 2147483647) return '箱数超出可保存范围。';
  if (item.unitsPerCarton && (!Number.isSafeInteger(item.unitsPerCarton) || item.unitsPerCarton <= 0 || item.unitsPerCarton > 2147483647)) return '每箱数量必须为有效正整数。';
  if (item.tailCartons > 0 && (!Number.isSafeInteger(item.tailUnitsPerCarton) || Number(item.tailUnitsPerCarton) <= 0)) return '请填写每个尾箱的实际数量。';
  if (!item.tailCartons && item.tailUnitsPerCarton) return '已填写尾箱数量，请填写尾箱箱数，或清空尾箱数量。';
  if (!item.id && item.fullCartons > 0 && !item.unitsPerCarton && availableCartonSpecs(item.product).length > 1) return '该产品有多种每箱数量，请选择本次完整箱规格。';
  if ((!item.id || item.product?.defaultUnitsPerCarton != null) && !item.quantityUnknown && item.fullCartons > 0 && !item.unitsPerCarton) return '请填写完整箱每箱数量。';
  const total = item.manualTotal ? item.totalUnits : calculateQuantity(item);
  if (total != null && (!Number.isSafeInteger(total) || total < 0)) return '总数量必须为可精确保存的非负整数。';
  return '';
}
