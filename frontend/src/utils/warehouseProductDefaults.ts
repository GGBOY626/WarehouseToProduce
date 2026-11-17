import type { Product } from '../types';

function values(items?:string[],fallback?:string){
  const result=[...new Set((items?.length?items:[fallback]).map(value=>value?.trim()).filter((value):value is string=>Boolean(value)))];
  return result;
}

export function warehouseProductDefaults(product:Product|undefined,finished:boolean){
  const codes=values(product?.materialCodes,product?.materialCode);
  const productionNumbers=finished?[]:values(product?.productionNumbers);
  const materialBatches=finished?[]:values(product?.materialBatches,product?.materialBatch);
  return {
    materialCodeOptions:finished?[]:codes,
    productionNumberOptions:productionNumbers,
    rawMaterialBatchOptions:materialBatches,
    batchOptions:finished?codes:[],
    materialCode:finished?product?.sku||'':codes.length===1?codes[0]||'':'',
    productionNumber:productionNumbers.length===1?productionNumbers[0]||'':'',
    rawMaterialBatch:materialBatches.length===1?materialBatches[0]||'':'',
    batch:finished&&codes.length===1?codes[0]||'':''
  };
}
