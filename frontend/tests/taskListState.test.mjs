import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readTaskListState, saveTaskListState } from '../src/utils/taskListState.ts';

test('detail navigation can restore filters, scroll and expanded shortages together', () => {
  let stored = null;
  globalThis.sessionStorage = { getItem: () => stored, setItem: (_, value) => { stored = value; } };
  const state = { showFinished: false, onlyAttention: true, scrollY: 840, expandedTaskIds: [12, 15] };
  saveTaskListState(state);
  assert.deepEqual(readTaskListState(), state);
  stored = JSON.stringify({showFinished:true, scrollY:123});
  assert.deepEqual(readTaskListState(), {showFinished:true, onlyAttention:false, scrollY:123, expandedTaskIds:[]});
  stored = JSON.stringify({expandedTaskIds:[1,'2',null,-1,1.5], scrollY:-3});
  assert.deepEqual(readTaskListState().expandedTaskIds, [1]);
  assert.equal(readTaskListState().scrollY, 0);
  delete globalThis.sessionStorage;
});
