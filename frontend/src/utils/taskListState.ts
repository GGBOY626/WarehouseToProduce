const KEY = 'production-task-list:v1';
export interface TaskListState { scrollY: number; expandedTaskIds?: number[] }
export function readTaskListState(): TaskListState {
  try {
    const value = JSON.parse(sessionStorage.getItem(KEY) || 'null');
    return { expandedTaskIds: Array.isArray(value?.expandedTaskIds) ? value.expandedTaskIds.filter((id: unknown) => typeof id === 'number' && Number.isSafeInteger(id) && id > 0) : [], scrollY: Number.isFinite(value?.scrollY) && value.scrollY >= 0 ? value.scrollY : 0 };
  } catch { return { scrollY: 0, expandedTaskIds: [] }; }
}
export function saveTaskListState(value: TaskListState) {
  try { sessionStorage.setItem(KEY, JSON.stringify(value)); } catch { /* Storage may be disabled. */ }
}
