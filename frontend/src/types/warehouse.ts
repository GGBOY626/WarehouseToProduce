export type WarehouseKind = 'RACK' | 'AISLE';
export interface WarehouseArea { id:number; name:string; kind:WarehouseKind; lanes:number; version:number; rackRow?:string|null }
export interface StockItem { productId?:number|null; productName:string; materialCode?:string|null; rawMaterialBatch?:string|null; batch?:string|null; unit?:string|null; quantity:number|null; cartons:number|null; photoId?:number|null; photoUrl?:string|null }
export interface WarehousePallet { id:number; code:string; areaId:number; areaName:string; kind:WarehouseKind; lane:string; position:number; location:string; notes?:string; items:StockItem[]; version:number; createdAt:string; updatedAt:string }
export interface PalletInput { code:string; areaId:number; lane:string; position:number; notes:string; items:{productId?:number; productName?:string; materialCode?:string; unit?:string; rawMaterialBatch?:string; batch:string; quantity:number|null; cartons:number|null; photoId?:number|null}[]; version?:number }
export interface WarehouseOverview { areas:WarehouseArea[]; pallets:WarehousePallet[] }
export interface WarehouseEvent { id:number; palletId?:number; palletCode?:string; action:string; description:string; beforeState?:string; afterState?:string; createdAt:string }
export interface WarehouseHistory { content:WarehouseEvent[]; totalPages:number; totalElements:number }
