import type { ProductionTask } from '../types';
export function taskAlerts(task: ProductionTask): string {
  const notes: string[] = [];
  const missing = task.targets.filter(t => t.materialTarget && t.shortageQuantity > 0).length;
  if (missing) notes.push(`${missing} 种物料缺料`);
  if (task.targets.some(t => t.unknownQuantityCount > 0) || task.output?.unknownQuantityCount) notes.push('有数量待确认');
  if (task.targets.some(t => !t.materialTarget)) notes.push('备料配置待核对');
  if ((task.output?.producedQuantity || 0) > (task.output?.netIssuedQuantity || 0)) notes.push('回仓数量待核对');
  return notes.join(' · ');
}
export function activeTask(task: ProductionTask) { return task.status === 'PREPARING' || task.status === 'IN_PROGRESS'; }
export function attentionFirst(tasks: ProductionTask[]) { return [...tasks].sort((a,b) => Number(Boolean(taskAlerts(b))) - Number(Boolean(taskAlerts(a)))); }
