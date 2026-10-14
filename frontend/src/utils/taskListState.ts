const KEY = 'production-task-list:v1';
export interface TaskListState { showFinished: boolean; onlyAttention?: boolean; scrollY: number }
export function readTaskListState(): TaskListState {
  try {
    const value = JSON.parse(sessionStorage.getItem(KEY) || 'null');
    return { showFinished: value?.showFinished === true, onlyAttention: value?.onlyAttention === true, scrollY: Number.isFinite(value?.scrollY) && value.scrollY >= 0 ? value.scrollY : 0 };
  } catch { return { showFinished: false, scrollY: 0 }; }
}
export function saveTaskListState(value: TaskListState) {
  try { sessionStorage.setItem(KEY, JSON.stringify(value)); } catch { /* Storage may be disabled. */ }
}
