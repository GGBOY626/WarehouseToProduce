import { http } from './http';
import type { ProductionRecipe, ProductionRecipeInput } from '../types';

export const productionRecipesApi = {
  list: async (outputProductId: number) => (await http.get<ProductionRecipe[]>(`/products/${outputProductId}/recipes`)).data,
  create: async (outputProductId: number, data: ProductionRecipeInput) => (await http.post<ProductionRecipe>(`/products/${outputProductId}/recipes`, data)).data,
  update: async (id: number, data: ProductionRecipeInput) => (await http.put<ProductionRecipe>(`/production-recipes/${id}`, data)).data,
  delete: async (id: number) => http.delete(`/production-recipes/${id}`),
};
