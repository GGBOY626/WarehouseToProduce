import { http } from './http';
import type { ProductionTask } from '../types';

export const createProductionTaskPreview = async (data: object) =>
  (await http.post<ProductionTask>('/production-tasks/preview', data)).data;

export const startProductionTask = async (id: number) =>
  (await http.patch<ProductionTask>(`/production-tasks/${id}/start`)).data;

export const saveTaskFindings = async (id: number, findings: { productId: number; found: boolean; foundLocation: string | null }[]) =>
  (await http.patch<ProductionTask>(`/production-tasks/${id}/findings`, { findings })).data;
