import { test } from 'node:test';
import assert from 'node:assert/strict';
import { formatDailyMovementCopy } from '../src/utils/dailyMovementCopy.ts';

const product = (name, code, quantity, unit, unknown = 0) => ({
  productName: name, materialCode: code, totalQuantity: quantity, baseUnit: unit, unknownItemCount: unknown,
});

test('copies the selected day and direction in the requested plain-text format', () => {
  const stats = { directions: [
    { direction: 'WAREHOUSE_TO_PRODUCTION', products: [
      product('清肺饮', '3B03700203H5', 5600, '条'),
      product('清肺饮包装盒', '3B03700403H101', 3600, '个'),
    ] },
    { direction: 'PRODUCTION_TO_WAREHOUSE', products: [product('成品', 'FIN', 99, '箱')] },
  ] };
  assert.equal(formatDailyMovementCopy('2026-10-21', 'WAREHOUSE_TO_PRODUCTION', stats),
    '日期：2026年10月21日\n方向：仓库 → 生产车间\n\nCode：3B03700203H5\n产品：清肺饮\n数量：5600条\n\nCode：3B03700403H101\n产品：清肺饮包装盒\n数量：3600个');
});

test('combines batches of one product but keeps codes and units separate', () => {
  const stats = { directions: [{ direction: 'WAREHOUSE_TO_PRODUCTION', products: [
    product('清肺饮', 'A', 1200, '条'), product('清肺饮', 'A', 400, '条'),
    product('清肺饮', 'B', 30, '盒'), product('清肺饮', 'A', 2, '条', 1),
  ] }] };
  const text = formatDailyMovementCopy('2026-10-21', 'WAREHOUSE_TO_PRODUCTION', stats);
  assert.match(text, /Code：A\n产品：清肺饮\n数量：1602条（另有 1 项数量待确认）/);
  assert.match(text, /Code：B\n产品：清肺饮\n数量：30盒/);
});

test('does not copy a false zero when all quantities are unknown or the day is empty', () => {
  const unknown = { directions: [{ direction: 'PRODUCTION_TO_WAREHOUSE', products: [product('成品', 'F', 0, '个', 2)] }] };
  assert.match(formatDailyMovementCopy('2026-10-21', 'PRODUCTION_TO_WAREHOUSE', unknown), /数量：待确认（2 项）/);
  assert.match(formatDailyMovementCopy('2026-10-21', 'WAREHOUSE_TO_PRODUCTION', unknown), /当天暂无有效流转记录。/);
});
