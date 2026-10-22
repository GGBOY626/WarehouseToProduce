import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readHomeFilters, presetRange, singleDayRange, validDate } from '../src/utils/homeFilters.ts';

const today = '2026-10-12';
test('returning from detail restores week, inbound direction, search and expanded filters', () => {
  const saved = { ...presetRange('week', '2026-10-15'), direction: 'PRODUCTION_TO_WAREHOUSE', q: '关节饮', moreFilters: true, activePreset: 'week' };
  assert.deepEqual(readHomeFilters(JSON.stringify(saved), today), { ...saved, materialKind: 'all' });
});
test('a selected historical day requests only that day and survives revisiting', () => {
  for (const date of ['2026-09-28', '2026-10-01']) {
    const saved = { ...singleDayRange(date), direction: 'PRODUCTION_TO_WAREHOUSE', q: '', moreFilters: false, activePreset: 'single' };
    assert.equal(saved.from, date); assert.equal(saved.to, date);
    assert.deepEqual(readHomeFilters(JSON.stringify(saved), today), { ...saved, materialKind: 'all' });
  }
});
test('start-date-through-today mode stays separate from single-day mode', () => {
  const saved = { from: '2026-09-28', to: today, direction: 'WAREHOUSE_TO_PRODUCTION', q: '', moreFilters: true, activePreset: 'range' };
  assert.deepEqual(readHomeFilters(JSON.stringify(saved), today), { ...saved, materialKind: 'all' });
});
test('date presets retain their existing inclusive ranges across month boundaries', () => {
  assert.deepEqual(presetRange('today', today), { from: today, to: today });
  assert.deepEqual(presetRange('yesterday', '2026-10-01'), { from: '2026-09-30', to: '2026-09-30' });
  assert.deepEqual(presetRange('week', '2026-10-04'), { from: '2026-09-28', to: '2026-10-04' });
  assert.deepEqual(presetRange('twoWeeks', '2026-10-04'), { from: '2026-09-21', to: '2026-10-04' });
});
test('missing, corrupt or impossible saved dates safely fall back to today', () => {
  const fallback = readHomeFilters(null, today);
  for (const value of ['', '{', 'null', '{}', JSON.stringify({from:'2026-02-30',to:today,direction:'PRODUCTION_TO_WAREHOUSE',activePreset:'range'}),
    JSON.stringify({from:today,to:'2026-09-28',direction:'PRODUCTION_TO_WAREHOUSE',activePreset:'range'}),
    JSON.stringify({from:'2026-09-28',to:today,direction:'PRODUCTION_TO_WAREHOUSE',activePreset:'single'})]) {
    assert.deepEqual(readHomeFilters(value, today), fallback);
  }
  assert.equal(validDate('2024-02-29'), true);
  assert.equal(validDate('2026-02-29'), false);
});

test('material type persists and older filters default to all', () => {
  const base = readHomeFilters(null, today);
  for (const materialKind of ['finished', 'material']) {
    assert.equal(readHomeFilters(JSON.stringify({...base, materialKind}), today).materialKind, materialKind);
  }
  assert.equal(readHomeFilters(JSON.stringify({...base, materialKind:'invalid'}), today).materialKind, 'all');
});
test('return-only filter persists with inbound direction and the selected date range', () => {
  const saved = { ...readHomeFilters(null, today), ...presetRange('twoWeeks', today),
    direction: 'PRODUCTION_TO_WAREHOUSE', activePreset: 'twoWeeks', materialKind: 'return' };
  assert.deepEqual(readHomeFilters(JSON.stringify(saved), today), saved);
  assert.equal(readHomeFilters(JSON.stringify({ ...saved, direction: 'WAREHOUSE_TO_PRODUCTION' }), today).materialKind, 'all');
});
