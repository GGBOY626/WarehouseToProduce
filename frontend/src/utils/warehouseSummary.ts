import type { StockItem, WarehouseArea, WarehousePallet } from '../types/warehouse';
import { filterWarehousePallets, type WarehouseFilters } from './warehouse.ts';

export interface StockAmounts { quantities: Record<string, bigint>; pending: number; unitUnknown: number }
export interface BatchLocation extends StockAmounts { key: string; location: string; pallets: string[]; rawBatches: string[]; finished:boolean }
export interface MaterialBatch extends StockAmounts { key: string; label: string; missing: boolean; locations: BatchLocation[] }
export interface MaterialSummary extends StockAmounts { key: string; name: string; code: string; productId?: number | null; batches: MaterialBatch[] }
const amounts = (): StockAmounts => ({ quantities: Object.create(null), pending: 0, unitUnknown: 0 });
function add(target: StockAmounts, item: StockItem) {
  if (item.quantity == null || !Number.isSafeInteger(item.quantity) || item.quantity < 0) { target.pending++; return; }
  const unit = item.unit?.trim();
  if (!unit) { target.unitUnknown++; return; }
  target.quantities[unit] = (target.quantities[unit] ?? 0n) + BigInt(item.quantity);
}
export function amountLabel(value: StockAmounts): string {
  const confirmed = Object.entries(value.quantities).map(([unit, quantity]) => `${quantity.toLocaleString()} ${unit}`).join(' / ');
  return confirmed ? confirmed + (value.unitUnknown ? ' · 另有数量待确认（单位未录入）' : '') : '数量待确认';
}

// Identity is exact, never fuzzy. A manual code can join a catalog identity only
// when the full inventory has exactly one matching name/code catalog product.
export function summarizeWarehouse(pallets: WarehousePallet[], areas: WarehouseArea[], filters: WarehouseFilters): MaterialSummary[] {
  const unique = [...new Map(pallets.map(p => [p.id, p])).values()];
  const catalog = new Map<string, Set<number>>();
  const signature = (i: StockItem) => JSON.stringify([i.productName.trim(), i.materialCode?.trim() || '']);
  for (const p of unique) for (const i of p.items) if (i.productId != null) {
    const ids = catalog.get(signature(i)) ?? new Set<number>();
    ids.add(i.productId); catalog.set(signature(i), ids);
  }
  const scope = filterWarehousePallets(unique, areas, { ...filters, query: '', unknownOnly: false });
  const query = filters.query.normalize('NFKC').trim().toLocaleLowerCase();
  const materials = new Map<string, MaterialSummary>();
  for (const p of scope) p.items.forEach((item, index) => {
    if (filters.unknownOnly && item.quantity != null) return;
    if (filters.hideFinished && item.finished) return;
    if (query && ![item.productName, item.materialCode, item.batch, item.rawMaterialBatch, p.code, p.location, p.notes]
      .some(value => value?.normalize('NFKC').toLocaleLowerCase().includes(query))) return;
    const name = item.productName.trim(), code = item.materialCode?.trim() || '';
    const candidates = code ? catalog.get(signature(item)) : undefined;
    const productId = item.productId ?? (candidates?.size === 1 ? [...candidates][0] : undefined);
    const key = JSON.stringify([name, productId != null ? ['product', productId] : ['manual', code]]);
    let material = materials.get(key);
    if (!material) { material = { ...amounts(), key, name, code, productId, batches: [] }; materials.set(key, material); }
    const batch = item.batch?.trim();
    const batchKey = batch ? JSON.stringify(['batch', batch]) : JSON.stringify(['missing', p.id, index]);
    let group = material.batches.find(b => b.key === batchKey);
    if (!group) { group = { ...amounts(), key: batchKey, label: batch || '批号未录入', missing: !batch, locations: [] }; material.batches.push(group); }
    const locationKey = JSON.stringify([p.areaId, p.lane, p.position]);
    let location = group.locations.find(l => l.key === locationKey);
    if (!location) { location = { ...amounts(), key: locationKey, location: p.location, pallets: [], rawBatches: [], finished:Boolean(item.finished) }; group.locations.push(location); }
    else location.finished = location.finished && Boolean(item.finished);
    if (!location.pallets.includes(p.code)) location.pallets.push(p.code);
    const raw = item.rawMaterialBatch?.trim();
    if (raw && !location.rawBatches.includes(raw)) location.rawBatches.push(raw);
    for (const target of [material, group, location]) add(target, item);
  });
  return [...materials.values()].sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'));
}
