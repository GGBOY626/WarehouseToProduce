import type { Product } from '../types/index.ts';
import { availableCartonSpecs } from './cartonSpecs.ts';

type PackagingProduct = Pick<Product, 'baseUnit' | 'boxPackings' | 'cartonSpecs' | 'defaultUnitsPerCarton'>;
const valid = (value: number | null | undefined) => typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
const number = (value: number) => value.toLocaleString('zh-CN');

export function productPackagingLines(product: PackagingProduct): string[] {
  const unit = product.baseUnit?.trim();
  const packings = product.boxPackings || [];
  const specs = availableCartonSpecs(product);
  if (!packings.length && !specs.length) return ['箱规未配置'];
  if (!unit) return ['箱规不完整'];

  const lines: string[] = [];
  const packedSizes = new Set<number>();
  for (const packing of packings) {
    if (!valid(packing.boxesPerCarton) || !valid(packing.unitsPerBox) ||
        (packing.cartonsPerPallet != null && !valid(packing.cartonsPerPallet)) ||
        !valid(packing.boxesPerCarton * packing.unitsPerBox)) {
      lines.push('箱规不完整');
      continue;
    }
    const total = packing.boxesPerCarton * packing.unitsPerBox;
    packedSizes.add(total);
    lines.push(`${number(packing.boxesPerCarton)} 盒/箱 · ${number(packing.unitsPerBox)} ${unit}/盒 · ${number(total)} ${unit}/箱${packing.cartonsPerPallet ? ` · ${number(packing.cartonsPerPallet)} 箱/托` : ''}`);
  }
  const plain = specs.filter(spec => !packedSizes.has(spec));
  if (plain.length) lines.push(`${plain.map(spec => `${number(spec)} ${unit}/箱`).join(' · ')}`);
  return lines.length ? lines : ['箱规未配置'];
}
