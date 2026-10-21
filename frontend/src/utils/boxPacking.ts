import type { BoxPacking, ItemInput, Product } from '../types';

export function packingError(packing: Partial<BoxPacking>, baseUnit?: string): string {
  if (packing.cartonsPerPallet != null && (packing.cartonsPerPallet as unknown) !== '' && (!Number.isInteger(packing.cartonsPerPallet) || packing.cartonsPerPallet <= 0 || packing.cartonsPerPallet > 2147483647)) return '每托盘箱数必须为有效正整数，也可留空。';
  if (!Number.isInteger(packing.boxesPerCarton) || !Number.isInteger(packing.unitsPerBox) || Number(packing.boxesPerCarton)<=0 || Number(packing.unitsPerBox)<=0)
    return '请填写有效的每箱盒数和每盒数量。';
  if (Number(packing.boxesPerCarton)*Number(packing.unitsPerBox)>2147483647) return '每箱数量超出可保存范围。';
  if (baseUnit==='盒' && packing.unitsPerBox!==1) return '按盒计量时每盒数量填 1；按条计量请将基础单位设为条。';
  return '';
}
export function initialBoxPacking(product?: Product): BoxPacking | undefined {
  const packings=product?.boxPackings || [];
  const totals=new Set(packings.map(p=>p.boxesPerCarton*p.unitsPerBox));
  const legacy=[product?.defaultUnitsPerCarton,...(product?.cartonSpecs||[])].filter(n=>n!=null&&!totals.has(n));
  return packings.length===1&&!legacy.length ? packings[0] : undefined;
}
// Both factors and the tail box count live in the draft and movement snapshot;
// changing product defaults must never reinterpret a saved quantity.
export function applyBoxPacking(item: ItemInput, packing?: BoxPacking) {
  const previousBoxes=item.tailBoxesPerCarton;
  item.boxesPerCarton=packing?.boxesPerCarton ?? null;
  item.unitsPerBox=packing?.unitsPerBox ?? null;
  item.tailBoxesPerCarton=null;
  if (packing) {
    item.unitsPerCarton=packing.boxesPerCarton*packing.unitsPerBox;
    // Preserve actual legacy tail quantities when exactly representable in boxes.
    if (previousBoxes != null) item.tailBoxesPerCarton=previousBoxes;
    else if (item.tailUnitsPerCarton != null && item.tailUnitsPerCarton % packing.unitsPerBox===0)
      item.tailBoxesPerCarton=item.tailUnitsPerCarton/packing.unitsPerBox;
    else item.tailUnitsPerCarton=null;
  }
}
export function syncBoxQuantity(item: ItemInput) {
  if (item.boxesPerCarton==null && item.unitsPerBox==null) return;
  const valid=!packingError({boxesPerCarton:item.boxesPerCarton??undefined,unitsPerBox:item.unitsPerBox??undefined},item.product?.baseUnit);
  item.unitsPerCarton=valid ? Number(item.boxesPerCarton)*Number(item.unitsPerBox) : null;
  item.tailUnitsPerCarton=valid && item.tailCartons>0 && Number.isInteger(item.tailBoxesPerCarton) && Number(item.tailBoxesPerCarton)>0
    ? Number(item.tailBoxesPerCarton)*Number(item.unitsPerBox) : null;
  if (!item.tailCartons) item.tailBoxesPerCarton=null;
}
