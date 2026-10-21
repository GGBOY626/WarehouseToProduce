import type { PackedQuantity } from '../types/index.ts';

const number = (value: string) => BigInt(value).toLocaleString('zh-CN');
export function packingRemainder(value: PackedQuantity, unit: string): string {
  const parts: string[] = [];
  if(value.boxes !== '0') parts.push(`${number(value.boxes)} 盒`);
  if(value.units !== '0') parts.push(`${number(value.units)} ${unit}`);
  if(value.fraction) parts.push(`${value.fraction} ${unit}`);
  return parts.join(' + ');
}
export function palletSummary(value: PackedQuantity, unit: string): string {
  if(value.pallets == null) return '';
  const remainder = packingRemainder(value, unit);
  return `${number(value.pallets)} 托盘${value.palletCartons !== '0' ? ` + ${number(value.palletCartons!)} 箱` : ''}${remainder ? ` + ${remainder}` : ''}`;
}
export const formatExactCount = number;
