import { http } from './http';
import type { WarehouseOverview, WarehousePallet, PalletInput, WarehouseHistory } from '../types/warehouse';
export const warehouseApi={
  uploadPhoto:async(file:File)=>{const data=new FormData();data.append('file',file);return (await http.post<{id:number;url:string}>('/warehouse/photos',data,{timeout:30000})).data},
  discardPhoto:async(id:number)=>http.delete(`/warehouse/photos/${id}`),
  overview:async()=>(await http.get<WarehouseOverview>('/warehouse')).data,
  create:async(data:PalletInput)=>(await http.post<WarehousePallet>('/warehouse/pallets',data)).data,
  update:async(id:number,data:PalletInput)=>(await http.put<WarehousePallet>(`/warehouse/pallets/${id}`,data)).data,
  remove:async(id:number,version:number)=>http.delete(`/warehouse/pallets/${id}`,{params:{version}}),
  history:async(page=0,palletId?:number)=>(await http.get<WarehouseHistory>('/warehouse/history',{params:{page,palletId}})).data
};
