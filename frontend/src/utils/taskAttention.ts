import type { ProductionTask } from '../types';
export function taskAlerts(task: ProductionTask): string {
  const notes: string[] = [];
  const shortages = task.targets.filter(t => t.materialTarget && t.shortageQuantity > 0)
    .map(t => `${t.productName} 缺 ${t.shortageQuantity.toLocaleString()} ${t.baseUnit || '个'}${t.shortageCartonSize ? `，还需 ${Math.ceil(t.shortageQuantity / t.shortageCartonSize)} 箱（按 ${t.shortageCartonSize} ${t.baseUnit || '个'}/箱）` : ''}`);
  notes.push(...shortages);
  notes.push(...taskWarnings(task));
  return notes.join(' · ');
}
export function taskWarnings(task: ProductionTask): string[] {
  const notes: string[] = [];
  if (task.targets.some(t => t.unknownQuantityCount > 0) || task.output?.unknownQuantityCount) notes.push('有数量待确认');
  if (task.targets.some(t => !t.materialTarget)) notes.push('备料配置待核对');
  if ((task.output?.producedQuantity || 0) > (task.output?.netIssuedQuantity || 0)) notes.push('回仓数量待核对');
  return notes;
}
export function activeTask(task: ProductionTask) { return task.status === 'PREPARING' || task.status === 'IN_PROGRESS'; }
export function attentionFirst(tasks: ProductionTask[]) { return [...tasks].sort((a,b) => Number(Boolean(taskAlerts(b))) - Number(Boolean(taskAlerts(a)))); }
