import { http } from './http';
import type { WarehouseArea, WarehouseOverview, WarehousePallet, PalletInput, WarehouseHistory } from '../types/warehouse';
export const warehouseApi={
  overview:async()=>(await http.get<WarehouseOverview>('/warehouse')).data,
  create:async(data:PalletInput)=>(await http.post<WarehousePallet>('/warehouse/pallets',data)).data,
  update:async(id:number,data:PalletInput)=>(await http.put<WarehousePallet>(`/warehouse/pallets/${id}`,data)).data,
  remove:async(id:number,version:number)=>http.delete(`/warehouse/pallets/${id}`,{params:{version}}),
  createAisle:async(data:{name:string;lanes:number})=>(await http.post<WarehouseArea>('/warehouse/aisles',data)).data,
  updateAisle:async(id:number,data:{name:string;lanes:number;version:number})=>(await http.put<WarehouseArea>(`/warehouse/aisles/${id}`,data)).data,
  removeAisle:async(id:number,version:number)=>http.delete(`/warehouse/aisles/${id}`,{params:{version}}),
  history:async(page=0,palletId?:number)=>(await http.get<WarehouseHistory>('/warehouse/history',{params:{page,palletId}})).data
};
