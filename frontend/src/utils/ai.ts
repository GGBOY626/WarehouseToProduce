import type { AiScope, Amounts } from '../api/ai';
const lanes:Record<string,string>={UPPER:'上层',MIDDLE:'中层',LOWER:'下层',LEFT_1:'左一',LEFT_2:'左二',RIGHT_1:'右一',RIGHT_2:'右二'};
export function aiScopeLabel(scope:AiScope):string {
  if(scope.taskId)return `当前生产任务 #${scope.taskId}`;
  return [scope.row?`${scope.row} 排`:'',scope.kind==='RACK'?'货架':scope.kind==='AISLE'?'过道':'',scope.areaId?`库区 #${scope.areaId}`:'',scope.lane?lanes[scope.lane]:'',scope.query?`关键词：${scope.query}`:'',scope.unknownOnly?'只看待盘点':'',scope.finished===true?'成品':scope.finished===false||scope.hideFinished?'正常物料':''].filter(Boolean).join(' · ')||'全仓已录入托盘台账';
}
export function aiAmount(value:Amounts):string {
  return Object.entries(value.quantities).map(([unit,n])=>`${BigInt(n).toLocaleString()} ${unit}`).join(' / ')||'数量待确认';
}
export function aiRecordLink(kind:'pallet'|'movement'|'task',id:number):string {
  if(!Number.isSafeInteger(id)||id<=0)return '';
  return kind==='pallet'?`/warehouse?palletId=${id}`:kind==='task'?`/production-tasks/${id}`:`/movements/${id}`;
}
