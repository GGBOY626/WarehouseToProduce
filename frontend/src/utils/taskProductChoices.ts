import type { Product, ProductionTask } from '../types';

export function taskProductChoices(catalog: Product[], tasks: ProductionTask[], materials: boolean, selectedId?: number): Product[] {
  const productIds = new Set(tasks.filter(task => task.status === 'PREPARING' || task.status === 'IN_PROGRESS').flatMap(task =>
    materials ? task.targets.filter(target => target.materialTarget).map(target => target.productId)
      : task.output ? [task.output.productId] : [],
  ));
  return catalog.filter(product => productIds.has(product.id) || product.id === selectedId);
}
