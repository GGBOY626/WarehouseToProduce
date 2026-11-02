import type { Product } from '../types';

export function movementProductChoices(catalog: Product[], selectedId?: number, taskProductIds?: readonly number[]): Product[] {
  const recommended = taskProductIds === undefined ? undefined : new Set(taskProductIds);
  return catalog.filter(product => (product.active && (recommended === undefined || recommended.has(product.id))) || product.id === selectedId);
}
