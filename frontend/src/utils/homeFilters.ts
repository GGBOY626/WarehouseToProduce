import type { Direction } from '../types';

export type DatePreset = 'today' | 'yesterday' | 'week' | 'twoWeeks' | 'single' | 'range';
export interface HomeFilters {
  from: string;
  to: string;
  direction: Direction;
  q: string;
  materialKind?: 'all' | 'finished' | 'material' | 'return';
  moreFilters: boolean;
  activePreset: DatePreset;
}
export const HOME_FILTERS_KEY = 'movement-list-filters:v1';

export function validDate(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}
export function shiftDate(date: string, days: number) {
  const value = new Date(`${date}T00:00:00Z`);
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}
export function presetRange(kind: 'today' | 'yesterday' | 'week' | 'twoWeeks', current: string) {
  if (kind === 'today') return { from: current, to: current };
  if (kind === 'yesterday') { const date = shiftDate(current, -1); return { from: date, to: date }; }
  const daysSinceMonday = (new Date(`${current}T00:00:00Z`).getUTCDay() + 6) % 7;
  return { from: shiftDate(current, -daysSinceMonday - (kind === 'twoWeeks' ? 7 : 0)), to: current };
}
export function singleDayRange(date: string) { return { from: date, to: date }; }
export function readHomeFilters(raw: string | null, current: string): HomeFilters {
  const defaults: HomeFilters = { from: current, to: current, direction: 'WAREHOUSE_TO_PRODUCTION', q: '', materialKind: 'all', moreFilters: false, activePreset: 'today' };
  try {
    const saved = JSON.parse(raw || 'null');
    if (!saved || !validDate(saved.from) || !validDate(saved.to) || saved.from > saved.to ||
        !['WAREHOUSE_TO_PRODUCTION', 'PRODUCTION_TO_WAREHOUSE'].includes(saved.direction) ||
        !['today', 'yesterday', 'week', 'twoWeeks', 'single', 'range'].includes(saved.activePreset)) return defaults;
    if (saved.activePreset === 'single' && saved.from !== saved.to) return defaults;
    return { from: saved.from, to: saved.to, direction: saved.direction,
      q: typeof saved.q === 'string' ? saved.q : '', materialKind: ['finished','material','return'].includes(saved.materialKind) && (saved.materialKind !== 'return' || saved.direction === 'PRODUCTION_TO_WAREHOUSE') ? saved.materialKind : 'all', moreFilters: saved.moreFilters === true, activePreset: saved.activePreset };
  } catch { return defaults; }
}
