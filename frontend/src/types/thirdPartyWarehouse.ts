export interface ThirdPartyWarehouse { id:number; name:string; location:string; version:number }
export interface ThirdPartyWarehouseInput { name:string; location:string; version?:number }
export interface ThirdPartyStockItem {
  productId?:number|null; productName:string; materialCode?:string|null; productionNumber?:string|null;
  materialBatch?:string|null; unit?:string|null; batch?:string|null; finished?:boolean|null; quantity:number|null; cartons:number|null;
}
export interface ThirdPartyStock {
  id:number; warehouseId:number; warehouseName:string; warehouseLocation:string; warehouseNumber:string|null;
  notes?:string|null; items:ThirdPartyStockItem[]; version:number; createdAt:string; updatedAt:string;
}
export interface ThirdPartyStockInput {
  warehouseId:number; warehouseNumber?:string|null; notes:string; items:ThirdPartyStockItem[]; version?:number;
}
export interface ThirdPartyOverview { warehouses:ThirdPartyWarehouse[]; stocks:ThirdPartyStock[] }
