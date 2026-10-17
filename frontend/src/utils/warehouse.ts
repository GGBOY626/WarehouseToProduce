import type { WarehouseArea, WarehousePallet } from '../types/warehouse';
export const laneLabels:Record<string,string>={UPPER:'上层',MIDDLE:'中层',LOWER:'下层',LEFT_1:'左一',LEFT_2:'左二',RIGHT_1:'右一',RIGHT_2:'右二'};
export function areaLanes(area?:WarehouseArea):string[]{
  if(!area)return [];
  return area.kind==='RACK'?['UPPER','MIDDLE','LOWER']:area.lanes===4?['LEFT_1','LEFT_2','RIGHT_1','RIGHT_2']:['LEFT_1','RIGHT_1'];
}
export function nextPosition(pallets:WarehousePallet[],areaId:number,lane:string):number{
  const used=new Set(pallets.filter(p=>p.areaId===areaId&&p.lane===lane).map(p=>p.position));
  let position=1;while(used.has(position)&&position<=9999)position++;return position;
}
export function filterPallets(pallets:WarehousePallet[],query:string,areaId?:number,lane?:string,unknownOnly=false){
  const q=query.trim().toLocaleLowerCase();
  return pallets.filter(p=>(!areaId||p.areaId===areaId)&&(!lane||p.lane===lane)&&(!unknownOnly||p.items.some(i=>i.quantity==null))&&(!q||[p.code,p.location,p.notes,...p.items.flatMap(i=>[i.productName,i.materialCode,i.rawMaterialBatch,i.batch])].filter(Boolean).join(' ').toLocaleLowerCase().includes(q)));
}
