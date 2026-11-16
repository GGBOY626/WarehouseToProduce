import { http } from './http';
import type { ThirdPartyOverview, ThirdPartyWarehouse, ThirdPartyWarehouseInput, ThirdPartyStock, ThirdPartyStockInput } from '../types/thirdPartyWarehouse';

const base='/warehouse/third-party';
export const thirdPartyWarehouseApi={
  overview:async()=>(await http.get<ThirdPartyOverview>(base)).data,
  createWarehouse:async(data:ThirdPartyWarehouseInput)=>(await http.post<ThirdPartyWarehouse>(base+'/warehouses',data)).data,
  updateWarehouse:async(id:number,data:ThirdPartyWarehouseInput)=>(await http.put<ThirdPartyWarehouse>(base+'/warehouses/'+id,data)).data,
  removeWarehouse:async(id:number,version:number)=>http.delete(base+'/warehouses/'+id,{params:{version}}),
  createStock:async(data:ThirdPartyStockInput)=>(await http.post<ThirdPartyStock>(base+'/stocks',data)).data,
  updateStock:async(id:number,data:ThirdPartyStockInput)=>(await http.put<ThirdPartyStock>(base+'/stocks/'+id,data)).data,
  removeStock:async(id:number,version:number)=>http.delete(base+'/stocks/'+id,{params:{version}})
};
