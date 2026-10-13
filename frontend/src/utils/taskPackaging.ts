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
