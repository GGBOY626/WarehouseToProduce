import type { Product } from '../types';

type Specs = Pick<Product, 'defaultUnitsPerCarton' | 'cartonSpecs' | 'boxPackings'>;

export function availableCartonSpecs(product?: Specs): number[] {
  return [...new Set([product?.defaultUnitsPerCarton, ...(product?.cartonSpecs || []), ...(product?.boxPackings || []).map(p=>p.boxesPerCarton*p.unitsPerBox)]
    .filter((n): n is number => Number.isInteger(n) && Number(n) > 0))].sort((a, b) => a - b);
}

// Multiple presets always need an explicit choice, including when a legacy default exists.
export function initialCartonSpec(product?: Specs): number | undefined {
  if ((product?.boxPackings?.length || 0) > 1) return undefined;
  const specs = availableCartonSpecs(product);
  return specs.length === 1 ? specs[0] : undefined;
}
