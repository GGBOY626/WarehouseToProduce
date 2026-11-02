import type { Product } from '../types';

export function movementProductChoices(catalog: Product[], selectedId?: number): Product[] {
  return catalog.filter(product => product.active || product.id === selectedId);
}
