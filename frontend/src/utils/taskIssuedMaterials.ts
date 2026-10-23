import type { ProductionTask } from '../types';

export function taskIssuedMaterials(task: ProductionTask) {
  const cartons = new Map<number, number>();
  for (const movement of task.movements) {
    if (movement.status !== 'ACTIVE' || movement.direction !== 'WAREHOUSE_TO_PRODUCTION') continue;
    for (const item of movement.items) {
      cartons.set(item.productId, (cartons.get(item.productId) || 0) + item.totalCartons);
    }
  }
  const materials = task.targets.filter(target => target.materialTarget).map(target => ({
    productId: target.productId,
    name: target.productName,
    cartons: cartons.get(target.productId) || 0,
    quantity: target.issuedQuantity,
    unit: target.baseUnit || '个',
    unknownQuantityCount: target.unknownQuantityCount,
  }));
  return { cartons: materials.reduce((sum, item) => sum + item.cartons, 0), materials };
}
