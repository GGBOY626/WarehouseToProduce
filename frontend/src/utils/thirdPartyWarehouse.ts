import type { Product } from '../types/index.ts';
import type { ThirdPartyStock } from '../types/thirdPartyWarehouse.ts';

const normalize=(value:string)=>value.normalize('NFKC').trim().toLocaleLowerCase();
export function thirdPartyProductChoices(products:Product[],query:string):Product[]{
  const q=normalize(query);
  return products.filter(p=>p.movementDirection==='WAREHOUSE_TO_PRODUCTION'&&(!q||normalize([
    p.name,p.materialCode,...p.materialCodes??[],p.materialBatch,...p.materialBatches??[],...p.productionNumbers??[]
  ].filter(Boolean).join(' ')).includes(q)));
}
export function productIdentifiers(product?:Product){
  const codes=[...new Set([product?.materialCode,...product?.materialCodes??[]].filter((s):s is string=>Boolean(s?.trim())).map(s=>s.trim()))];
  const batches=[...new Set([product?.materialBatch,...product?.materialBatches??[]].filter((s):s is string=>Boolean(s?.trim())).map(s=>s.trim()))];
  const numbers=[...new Set(product?.productionNumbers??[])];
  return {codes,batches,numbers};
}
export function thirdPartyProductDefaults(product?:Product){
  const {codes,batches,numbers}=productIdentifiers(product);
  return {
    productId:product?.id,productName:product?.name??'',unit:product?.baseUnit??'',
    materialCode:codes.length===1?codes[0]!:'',materialBatch:batches.length===1?batches[0]!:'',
    productionNumber:numbers.length===1?numbers[0]!:''
  };
}
export function filterThirdPartyStocks(stocks:ThirdPartyStock[],query:string,warehouseId?:number){
  const q=normalize(query);
  return stocks.filter(s=>(warehouseId===undefined||s.warehouseId===warehouseId)&&(!q||normalize([
    s.warehouseNumber,s.warehouseName,s.warehouseLocation,s.notes,
    ...s.items.flatMap(i=>[i.productName,i.materialCode,i.productionNumber,i.materialBatch,i.batch])
  ].filter(Boolean).join(' ')).includes(q)));
}
