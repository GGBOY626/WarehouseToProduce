import { test } from 'node:test';
import assert from 'node:assert/strict';
import { thirdPartyProductChoices, thirdPartyProductDefaults, filterThirdPartyStocks } from '../src/utils/thirdPartyWarehouse.ts';

const product={id:1,name:'测试原料',materialCode:'M-001',materialCodes:['M-001'],materialBatch:'B-001',materialBatches:['B-001'],
  productionNumbers:['P-001'],baseUnit:'条',active:true,movementDirection:'WAREHOUSE_TO_PRODUCTION'};

test('third party search includes every inbound product and searches all identifiers',()=>{
  const catalog=[product,{...product,id:2,name:'非任务产品',active:false},{...product,id:3,movementDirection:'PRODUCTION_TO_WAREHOUSE'}];
  assert.deepEqual(thirdPartyProductChoices(catalog,'').map(p=>p.id),[1,2]);
  assert.deepEqual(thirdPartyProductChoices(catalog,'测试原料').map(p=>p.id),[1]);
  for(const query of ['m-001','p-001','b-001','Ｍ－００１']){
    assert.deepEqual(thirdPartyProductChoices(catalog,query).map(p=>p.id),[1,2]);
  }
  assert.deepEqual(thirdPartyProductChoices(catalog,'非任务产品').map(p=>p.id),[2]);
});

test('unique product values fill automatically and ambiguous identifiers stay empty',()=>{
  assert.deepEqual(thirdPartyProductDefaults(product),{
    productId:1,productName:'测试原料',materialCode:'M-001',productionNumber:'P-001',materialBatch:'B-001',unit:'条'
  });
  const multiple={...product,materialCodes:['M-001','M-002'],productionNumbers:['P-001','P-002'],materialBatches:['B-001','B-002']};
  const defaults=thirdPartyProductDefaults(multiple);
  assert.equal(defaults.materialCode,'');assert.equal(defaults.productionNumber,'');assert.equal(defaults.materialBatch,'');
  assert.equal(thirdPartyProductDefaults().productId,undefined);
  assert.equal(thirdPartyProductDefaults().materialBatch,'');
});

test('warehouse number preserves leading zeroes and combines with warehouse filter',()=>{
  const stocks=[
    {warehouseId:1,warehouseNumber:'00123-A',warehouseName:'北仓',warehouseLocation:'北区',items:[{productName:'原料',materialCode:'M-1',productionNumber:'P-1',materialBatch:'B-1'}]},
    {warehouseId:2,warehouseNumber:'00123-A',warehouseName:'南仓',warehouseLocation:'南区',items:[{productName:'外箱',materialCode:'M-2'}]}
  ];
  assert.equal(filterThirdPartyStocks(stocks,'00123-a').length,2);
  assert.deepEqual(filterThirdPartyStocks(stocks,'00123-A',1),[stocks[0]]);
  for(const q of ['P-1','B-1','原料','北区'])assert.deepEqual(filterThirdPartyStocks(stocks,q),[stocks[0]]);
  assert.deepEqual(filterThirdPartyStocks(stocks,'不存在'),[]);
  assert.equal(stocks[0].warehouseNumber,'00123-A');
});
