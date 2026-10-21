import type { Direction, QueryStats } from '../types/index.ts';
import { directionLabel } from './labels.ts';

export function formatDailyMovementCopy(date: string, direction: Direction, stats: QueryStats): string {
  const [year, month, day] = date.split('-').map(Number);
  const lines = [`日期：${year}年${month}月${day}日`, `方向：${directionLabel(direction)}`];
  const totals = new Map<string, { name: string; code: string; unit: string; quantity: number; unknown: number }>();

  for (const group of stats.directions.filter(group => group.direction === direction)) {
    for (const product of group.products) {
      const key = JSON.stringify([product.productName, product.materialCode, product.baseUnit]);
      let total = totals.get(key);
      if (!total) {
        total = { name: product.productName, code: product.materialCode, unit: product.baseUnit || '', quantity: 0, unknown: 0 };
        totals.set(key, total);
      }
      total.quantity += product.totalQuantity;
      total.unknown += product.unknownItemCount;
      if (!Number.isSafeInteger(total.quantity)) throw new Error('数量超出浏览器可精确复制范围，请核对原始记录。');
    }
  }

  if (!totals.size) return [...lines, '', '当天暂无有效流转记录。'].join('\n');
  for (const product of totals.values()) {
    const quantity = product.unknown && product.quantity === 0
      ? `待确认（${product.unknown} 项）`
      : `${product.quantity}${product.unit}${product.unknown ? `（另有 ${product.unknown} 项数量待确认）` : ''}`;
    lines.push('', `Code：${product.code}`, `产品：${product.name}`, `数量：${quantity}`);
  }
  return lines.join('\n');
}
