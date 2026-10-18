import test from 'node:test';
import assert from 'node:assert/strict';
import {aiScopeLabel,aiAmount,aiRecordLink} from '../src/utils/ai.ts';
test('AI scope includes every location and inventory filter',()=>{
  const label=aiScopeLabel({row:'A',kind:'AISLE',areaId:3,lane:'LEFT_2',query:'22344',unknownOnly:true});
  for(const part of ['A 排','过道','库区 #3','左二','22344','只看待盘点'])assert.ok(label.includes(part));
  assert.equal(aiScopeLabel({taskId:42}),'当前生产任务 #42');
});
test('AI quantities are exact strings by unit; unknown never becomes zero',()=>{
  assert.equal(aiAmount({quantities:{},pending:1}),'数量待确认');
  assert.equal(aiAmount({quantities:{件:'18014398509481982',kg:'5'}}),'18,014,398,509,481,982 件 / 5 kg');
});
test('AI record links use only existing routes and positive IDs',()=>{
  assert.equal(aiRecordLink('pallet',12),'/warehouse?palletId=12');
  assert.equal(aiRecordLink('movement',14),'/movements/14');
  assert.equal(aiRecordLink('task',15),'/production-tasks/15');
  assert.equal(aiRecordLink('task',NaN),'');assert.equal(aiRecordLink('pallet',-1),'');
});
