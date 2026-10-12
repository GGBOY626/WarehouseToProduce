import type { Product } from '../types';

type Specs = Pick<Product, 'defaultUnitsPerCarton' | 'cartonSpecs'>;

export function availableCartonSpecs(product?: Specs): number[] {
  return [...new Set([product?.defaultUnitsPerCarton, ...(product?.cartonSpecs || [])]
    .filter((n): n is number => Number.isInteger(n) && Number(n) > 0))].sort((a, b) => a - b);
}

// Multiple presets always need an explicit choice, including when a legacy default exists.
export function initialCartonSpec(product?: Specs): number | undefined {
  const specs = availableCartonSpecs(product);
  return specs.length === 1 ? specs[0] : undefined;
}
