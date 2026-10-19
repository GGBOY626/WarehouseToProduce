import type { WarehouseArea, WarehousePallet } from '../types/warehouse';
export const laneLabels:Record<string,string>={UPPER:'上层',MIDDLE:'中层',LOWER:'下层',LEFT_1:'左一',LEFT_2:'左二',RIGHT_1:'右一',RIGHT_2:'右二'};
export function areaLanes(area?:WarehouseArea):string[]{
  if(!area)return [];
  return area.kind==='RACK'?['UPPER','MIDDLE','LOWER']:area.lanes===4?['LEFT_1','LEFT_2','RIGHT_1','RIGHT_2']:['LEFT_1','RIGHT_1'];
}
export function nextPosition(pallets:WarehousePallet[],areaId:number,lane:string,start=5):number{
  const used=new Set(pallets.filter(p=>p.areaId===areaId&&p.lane===lane).map(p=>p.position));
  let position=start;while(used.has(position)&&position<=(start===5?16:9999))position++;return position;
}
export function filterPallets(pallets:WarehousePallet[],query:string,areaId?:number,lane?:string,unknownOnly=false){
  const q=query.normalize('NFKC').trim().toLocaleLowerCase();
  return pallets.filter(p=>(!areaId||p.areaId===areaId)&&(!lane||p.lane===lane)&&(!unknownOnly||p.items.some(i=>i.quantity==null))&&(!q||[p.code,p.location,p.notes,...p.items.flatMap(i=>[i.productName,i.materialCode,i.rawMaterialBatch,i.batch])].filter(Boolean).join(' ').normalize('NFKC').toLocaleLowerCase().includes(q)));
}

export interface WarehouseFilters {
  query: string;
  kind: '' | 'RACK' | 'AISLE';
  row: string;
  areaId?: number;
  lane: string;
  unknownOnly: boolean;
  hideFinished: boolean;
  finished?: boolean;
}
export function emptyWarehouseFilters(): WarehouseFilters {
  return {query:'',kind:'',row:'',lane:'',unknownOnly:false,hideFinished:false};
}
export function warehouseRow(area?: WarehouseArea): string {
  return area?.kind==='RACK' ? area.name : area?.rackRow || '';
}
export function filterWarehousePallets(pallets:WarehousePallet[],areas:WarehouseArea[],filters:WarehouseFilters) {
  const areaById=new Map(areas.map(area=>[area.id,area]));
  const finished=filters.finished ?? (filters.hideFinished ? false : undefined);
  const typed=finished===undefined?pallets:pallets.map(p=>({...p,items:p.items.filter(i=>Boolean(i.finished)===finished)})).filter(p=>p.items.length>0);
  return filterPallets(typed,filters.query,filters.areaId,filters.lane,filters.unknownOnly)
    .filter(p=>(!filters.kind||p.kind===filters.kind)&&(!filters.row||warehouseRow(areaById.get(p.areaId))===filters.row));
}
// Pending is a separate dimension, never an additional location count.
export function warehouseStats(pallets:WarehousePallet[]) {
  return {
    total:pallets.length,
    rack:pallets.filter(p=>p.kind==='RACK').length,
    aisle:pallets.filter(p=>p.kind==='AISLE').length,
    pending:pallets.filter(p=>p.items.some(item=>item.quantity==null)).length
  };
}
// Board selection preserves text/counting filters while synchronizing location controls.
export function selectWarehouseLocation(filters:WarehouseFilters,area:WarehouseArea,lane=''):WarehouseFilters {
  return {...filters,areaId:area.id,lane,kind:area.kind,row:warehouseRow(area)};
}
export function changeWarehouseScope(filters:WarehouseFilters,scope:Partial<Pick<WarehouseFilters,'kind'|'row'>>):WarehouseFilters {
  return {...filters,...scope,areaId:undefined,lane:''};
}
