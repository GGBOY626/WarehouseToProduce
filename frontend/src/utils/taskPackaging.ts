import type { ProductionTaskOutput } from '../types';

type Packing = Pick<ProductionTaskOutput, 'boxesPerCarton' | 'unitsPerBox' | 'quantityBasis'>;

export function unitsForBoxes(packing: Packing, boxes: number): number {
  return boxes * (packing.quantityBasis === 'UNIT' ? packing.unitsPerBox! : 1);
}
export function taskCartonUnits(packing: Packing): number {
  return unitsForBoxes(packing, packing.boxesPerCarton!);
}
export function boxesForUnits(packing: Packing, units: number): number {
  return units / (packing.quantityBasis === 'UNIT' ? packing.unitsPerBox! : 1);
}

export function fullCartonsForPalletSelection(current: number, cartonsPerPallet: number | null | undefined, selected: boolean): number {
  return selected && Number.isSafeInteger(cartonsPerPallet) && Number(cartonsPerPallet) > 0
    ? Number(cartonsPerPallet)
    : current;
}

export function tailCartonsOnOpen(current: number | undefined, touched: boolean, defaultToOne: boolean): number {
  return defaultToOne && !touched && Number(current || 0) === 0 ? 1 : current ?? 0;
}
