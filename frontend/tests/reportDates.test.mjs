import { test } from 'node:test';
import assert from 'node:assert/strict';
import { previousMonday, shiftDate } from '../src/utils/reportDates.ts';

test('production task range begins on the previous calendar week Monday', () => {
  assert.equal(previousMonday('2026-10-22'), '2026-10-12'); // Thursday
  assert.equal(previousMonday('2026-10-19'), '2026-10-12'); // Monday
  assert.equal(previousMonday('2026-11-01'), '2026-10-19'); // Sunday, month boundary
});

test('rolling two-week range includes today and the previous thirteen days', () => {
  assert.equal(shiftDate('2026-11-19', -13), '2026-11-06');
  assert.equal(shiftDate('2026-03-05', -13), '2026-02-20');
});
