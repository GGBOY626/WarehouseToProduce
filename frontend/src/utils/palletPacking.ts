import type { ItemInput, ProductionTaskOption } from '../types/index.ts';
import { availableCartonSpecs } from './cartonSpecs.ts';

const positive = (value: number | null | undefined): value is number => Number.isSafeInteger(value) && Number(value) > 0;

export function palletPacking(item: Pick<ItemInput, 'product' | 'boxesPerCarton' | 'unitsPerBox' | 'unitsPerCarton'>, task?: ProductionTaskOption) {
  if (positive(task?.outputCartonsPerPallet)) return { cartonsPerPallet: task.outputCartonsPerPallet, requiresPackingSelection: false };
  const packings = item.product?.boxPackings || [];
  const boxes = task?.outputBoxesPerCarton ?? item.boxesPerCarton;
  const units = task?.outputUnitsPerBox ?? item.unitsPerBox;
  const hasFactors = boxes != null || units != null;
  const candidates = hasFactors
    ? packings.filter(p => p.boxesPerCarton === boxes && p.unitsPerBox === units)
    : positive(item.unitsPerCarton)
      ? packings.filter(p => p.boxesPerCarton * p.unitsPerBox === item.unitsPerCarton)
      : packings;
  const hasLegacyChoice = !hasFactors && !positive(item.unitsPerCarton) && availableCartonSpecs(item.product)
    .some(size => !packings.some(p => p.boxesPerCarton * p.unitsPerBox === size));
  const counts = new Set(candidates.map(p => p.cartonsPerPallet));
  const count = candidates[0]?.cartonsPerPallet;
  // Several box sizes may share one pallet size; never guess when they differ.
  if (!hasLegacyChoice && counts.size === 1 && positive(count)) return { cartonsPerPallet: count, requiresPackingSelection: false };
  return { cartonsPerPallet: undefined, requiresPackingSelection: !hasFactors && candidates.some(p => positive(p.cartonsPerPallet)) };
}
