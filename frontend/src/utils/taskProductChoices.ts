import type { Product } from '../types';

export function taskProductChoices(catalog: Product[], productIds: number[], selectedId?: number): Product[] {
  const allowed = new Set(productIds);
  return catalog.filter(product => allowed.has(product.id) || product.id === selectedId);
}
