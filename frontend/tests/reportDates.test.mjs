import { test } from 'node:test';
import assert from 'node:assert/strict';
import { previousMonday } from '../src/utils/reportDates.ts';

test('production task range begins on the previous calendar week Monday', () => {
  assert.equal(previousMonday('2026-10-22'), '2026-10-12'); // Thursday
  assert.equal(previousMonday('2026-10-19'), '2026-10-12'); // Monday
  assert.equal(previousMonday('2026-11-01'), '2026-10-19'); // Sunday, month boundary
});
