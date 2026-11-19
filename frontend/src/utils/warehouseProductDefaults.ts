import type { Product } from '../types';

function values(items?:string[],fallback?:string){
  const result=[...new Set((items?.length?items:[fallback]).map(value=>value?.trim()).filter((value):value is string=>Boolean(value)))];
  return result;
}

export function warehouseProductDefaults(product:Product|undefined,finished:boolean){
  const catalogCodes=values(product?.materialCodes,product?.materialCode);
  const codes=finished
    ? product?.sku ? (catalogCodes.includes(product.sku)?catalogCodes:[product.sku]) : []
    : catalogCodes;
  const productionNumbers=finished?[]:values(product?.productionNumbers);
  const materialBatches=finished?[]:values(product?.materialBatches,product?.materialBatch);
  const batches=finished?values(product?.productionNumbers):[];
  return {
    materialCodeOptions:codes,
    productionNumberOptions:productionNumbers,
    rawMaterialBatchOptions:materialBatches,
    batchOptions:batches,
    materialCode:finished?(codes.length===1?codes[0]||'':''):codes.length===1?codes[0]||'':'',
    productionNumber:productionNumbers.length===1?productionNumbers[0]||'':'',
    rawMaterialBatch:materialBatches.length===1?materialBatches[0]||'':'',
    batch:batches.length===1?batches[0]||'':''
  };
}
