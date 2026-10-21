// Isolated UI fixture: npm run build, then node tests/fixtures/movement-list-server.mjs.
// Uses synthetic records only; never connects to business services or a database.
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { resolve, extname, sep } from 'node:path';
const dist = resolve(fileURLToPath(new URL('../../dist/', import.meta.url)));
const outbound = 'WAREHOUSE_TO_PRODUCTION', inbound = 'PRODUCTION_TO_WAREHOUSE';
const material = (id, name, unknown = false) => ({
  id, productId: id, productName: name, materialCode: `QA-${id}`, materialBatch: '20261020-A',
  baseUnit: '个', fullCartons: 24, tailCartons: 0, totalCartons: 24, looseUnits: 0,
  totalQuantity: unknown ? null : 120000, quantityUnknown: unknown, packaging: [],
});
const record = (id, direction, items, extra = {}) => ({
  id, recordNo: `QA-${id}`, direction, movementTime: '2026-10-20T02:30:00Z',
  returnMovement: false, finished: direction === inbound, status: 'ACTIVE',
  items, itemCount: items.length, photoCount: 3, hasIssues: false,
  totalCartons: items.length * 24, ...extra,
});
const records = [
  record(1, outbound, [material(1, '关节饮外盒')], { productionTaskId: 1, productionTaskName: '关节饮 · 十月生产计划' }),
  record(2, outbound, [material(2, '小绿芯铁')]),
  record(3, outbound, [material(3, '海外版关节饮特别规格超长物料名称用于核对移动端换行和关键数量完整显示')]),
  record(4, outbound, [material(4, '外盒'), material(5, '内托', true)], { hasIssues: true, photoCount: 0 }),
  record(5, inbound, [material(6, '关节饮')], { productionTaskId: 1, productionTaskName: '关节饮 · 十月生产计划' }),
  record(6, inbound, [material(7, '小绿芯')]),
  record(7, inbound, [material(8, '关节饮外盒')], { returnMovement: true, finished: false, photoCount: 0 }),
];
const products = [
  { id: 1, name: '关节饮', materialCode: 'RAW-1', movementDirection: outbound, duplicateName: false },
  { id: 2, name: '关节饮', materialCode: 'FIN-1', movementDirection: inbound, duplicateName: false },
  { id: 3, name: '小绿芯', materialCode: 'FIN-2', movementDirection: inbound, duplicateName: true },
  { id: 4, name: '小绿芯', materialCode: 'FIN-3', movementDirection: inbound, duplicateName: true },
].map(p => ({ ...p, active: true, baseUnit: '个', quantityUnknown: false, cartonSpecs: [5000], materialBatches: ['A'], boxPackings: [] }));
const detail = row => ({ ...row, materials: row.items, photos: [], senderName: 'QA', receiverName: 'QA', items: row.items.map(i => ({ ...i, batchNo: i.materialCode, totalUnits: i.totalQuantity, issues: [] })) });
const types = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html', '.svg': 'image/svg+xml' };
createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost'), path = url.pathname;
  const json = data => { res.setHeader('Content-Type', 'application/json; charset=utf-8'); res.end(JSON.stringify(data)); };
  if (path === '/api/auth/status') return json({ authenticated: true, username: 'UI QA' });
  if (path === '/api/products') return json(products.filter(p => (!url.searchParams.get('direction') || p.movementDirection === url.searchParams.get('direction')) && `${p.name} ${p.materialCode}`.includes(url.searchParams.get('q') || '')));
  const selected = records.filter(r => (!url.searchParams.get('direction') || r.direction === url.searchParams.get('direction')) && (!url.searchParams.has('finished') || r.finished === (url.searchParams.get('finished') === 'true')) && r.items.some(i => `${i.productName} ${i.materialCode} ${i.materialBatch}`.includes(url.searchParams.get('q') || '')));
  if (path === '/api/movements') return json({ content: selected, totalElements: selected.length, totalPages: 1 });
  if (path === '/api/movements/stats/query') return json({ directions: selected.length ? [{ direction: selected[0].direction, totalCartons: selected.reduce((sum, r) => sum + r.totalCartons, 0), unknownItemCount: 0, products: selected.flatMap(r => r.items.map(i => ({ ...i, movements: [r] }))) }] : [] });
  if (/^\/api\/movements\/\d+$/.test(path)) return json(detail(records.find(r => r.id === Number(path.split('/').at(-1)))));
  if (path === '/api/production-tasks/linkable') return json([{ id: 1, name: '关节饮 · 十月生产计划', batchNo: 'QA', status: 'IN_PROGRESS' }]);
  if (/^\/api\/movements\/\d+\/production-task$/.test(path)) return json({ productionTaskId: 1, productionTaskName: '关节饮 · 十月生产计划' });
  if (path.startsWith('/api/')) return json([]);
  const file = path.startsWith('/assets/') ? resolve(dist, path.slice(1)) : resolve(dist, 'index.html');
  if (!file.startsWith(dist + sep)) { res.writeHead(403); return res.end(); }
  try { res.setHeader('Content-Type', types[extname(file)] || 'application/octet-stream'); res.end(await readFile(file)); }
  catch { res.writeHead(404); res.end('Not found'); }
}).listen(5193, '127.0.0.1', () => console.log('Movement UI fixture: http://127.0.0.1:5193'));
