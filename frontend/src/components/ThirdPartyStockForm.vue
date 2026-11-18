<script setup lang="ts">
import { ref } from 'vue';
import { thirdPartyWarehouseApi } from '../api/thirdPartyWarehouse';
import { errorMessage } from '../api/http';
import SearchPicker from './SearchPicker.vue';
import { productsApi } from '../api';
import { warehouseProductDefaults } from '../utils/warehouseProductDefaults';
import type { Product } from '../types';
import type { ThirdPartyWarehouse, ThirdPartyStock, ThirdPartyStockItem, ThirdPartyStockInput } from '../types/thirdPartyWarehouse';

const props=defineProps<{warehouses:ThirdPartyWarehouse[];products:Product[];stock?:ThirdPartyStock;initialWarehouseId?:number;catalogError?:string}>();
const emit=defineEmits<{saved:[stock:ThirdPartyStock];cancel:[]}>();
type DraftItem=Omit<ThirdPartyStockItem,'quantity'|'cartons'>&{key:number;finished:boolean|null;quantity:number|null|'';cartons:number|null|''};
const options=ref<Record<number,ReturnType<typeof warehouseProductDefaults>>>({});
let key=0;
function blank():DraftItem{return {key:key++,finished:null,productName:'',quantity:null,cartons:null}}
const warehouseId=ref(props.stock?.warehouseId??props.initialWarehouseId??props.warehouses[0]?.id);
const warehouseNumber=ref(props.stock?.warehouseNumber??''),notes=ref(props.stock?.notes??'');
const items=ref<DraftItem[]>(props.stock?.items.map(i=>({...blank(),...i,finished:i.finished??false}))??[blank()]);
const busy=ref(false),message=ref('');
function setFinished(item:DraftItem,value:boolean){item.finished=value;item.productId=null;item.productName='';item.materialCode='';item.productionNumber='';item.materialBatch='';item.batch='';item.unit='';delete options.value[item.key]}
async function searchProducts(query:string,item:DraftItem){return productsApi.search(query,true,item.finished?'PRODUCTION_TO_WAREHOUSE':'WAREHOUSE_TO_PRODUCTION')}
function changedProduct(item:DraftItem,p?:Product){item.productName=p?.name||item.productName;item.unit=p?.baseUnit||'';const d=warehouseProductDefaults(p,item.finished===true);options.value[item.key]=d;item.productId=p?.id;item.materialCode=d.materialCode;item.batch=d.batch;item.materialBatch=d.rawMaterialBatch;item.productionNumber=d.productionNumber}
async function save(){
  if(busy.value)return;message.value='';
  if(!warehouseId.value||items.value.some(i=>i.finished===null||!i.productName.trim())){message.value='请选择第三方仓库，并为每项选择成品类型、填写产品名称。';return}
  const input:ThirdPartyStockInput={
    warehouseId:warehouseId.value,warehouseNumber:warehouseNumber.value.trim()||null,notes:notes.value.trim(),version:props.stock?.version,
    items:items.value.map(i=>({productId:i.productId??null,productName:i.productName.trim(),finished:i.finished,batch:i.batch?.trim(),
      materialCode:i.materialCode?.trim(),productionNumber:i.productionNumber?.trim(),materialBatch:i.materialBatch?.trim(),
      unit:i.unit?.trim(),quantity:i.quantity===''?null:i.quantity,cartons:i.cartons===''?null:i.cartons}))
  };
  busy.value=true;
  try{emit('saved',props.stock?await thirdPartyWarehouseApi.updateStock(props.stock.id,input):await thirdPartyWarehouseApi.createStock(input))}
  catch(e){message.value=errorMessage(e)}finally{busy.value=false}
}
</script>
<template>
  <form class="external-form" @submit.prevent="save">
    <header><h2>{{stock?'编辑第三方库存':'录入第三方库存'}}</h2><button class="btn btn-secondary" type="button" :disabled="busy" @click="emit('cancel')">关闭</button></header>
    <p v-if="message" class="error-text" role="alert">{{message}}</p>
    <p v-if="catalogError" class="error-text" role="alert">{{catalogError}}</p>
    <fieldset :disabled="busy">
      <legend class="sr-only">第三方库存资料</legend>
      <div class="field"><label for="external-stock-warehouse">第三方仓库</label><select id="external-stock-warehouse" v-model="warehouseId" class="select" required><option v-for="w in warehouses" :key="w.id" :value="w.id">{{w.name}} · {{w.location}}</option></select></div>
      <div class="number-field field"><label for="external-stock-number">第三方仓库号（选填）</label><input id="external-stock-number" v-model="warehouseNumber" class="input mono" maxlength="100" placeholder="可填写对方提供的仓库号，保留前导零"><span class="hint">调回货物时可使用此号码。</span></div>
      <div class="section-head"><h3>库存物料</h3><button class="btn btn-secondary" type="button" :disabled="items.length>=50" @click="items.push(blank())">＋ 添加物料</button></div>
      <p class="hint">每项先选择成品类型，再填写产品名称并搜索已有产品或直接输入。</p>
      <section v-for="(item,index) in items" :key="item.key" class="external-item">
        <div class="section-head"><strong>物料 {{index+1}}</strong><button v-if="items.length>1" class="btn btn-ghost" type="button" :aria-label="'移除物料 '+(index+1)" @click="items.splice(index,1)">移除</button></div>
        <div class="finish-choice"><span class="field-label required">是否成品</span><div><label><input v-model="item.finished" type="radio" :name="'finished-'+item.key" :value="true" @change="setFinished(item,true)">是，成品</label><label><input v-model="item.finished" type="radio" :name="'finished-'+item.key" :value="false" @change="setFinished(item,false)">不是成品</label></div></div>
        <template v-if="item.finished!==null"><SearchPicker :model-value="item.productId??undefined" @update:model-value="item.productId=$event??null" v-model:text-value="item.productName" free-text :max-length="200" label="产品名称" placeholder="输入产品名称或搜索已有产品" :load="query=>searchProducts(query,item)" :display="(p:Product)=>p.name" @select="changedProduct(item,$event as Product|undefined)"/>
        <div v-for="choice in (item.finished?[{field:'batch',label:'BATCH',values:options[item.key]?.batchOptions}]:[{field:'materialCode',label:'物料编码',values:options[item.key]?.materialCodeOptions},{field:'productionNumber',label:'生产编号',values:options[item.key]?.productionNumberOptions},{field:'materialBatch',label:'物料批次',values:options[item.key]?.rawMaterialBatchOptions}])" v-show="(choice.values?.length??0)>1" :key="choice.field" class="field"><label :for="'external-choice-'+choice.field+item.key">产品有多个{{choice.label}}，请选择</label><select :id="'external-choice-'+choice.field+item.key" v-model="item[choice.field as 'batch'|'materialCode'|'productionNumber'|'materialBatch']" class="select"><option value="">选择{{choice.label}}…</option><option v-for="value in choice.values" :key="value" :value="value">{{value}}</option></select></div>
        <template v-if="item.finished"><div class="field"><label :for="'finished-batch-'+item.key">BATCH（可选）</label><input :id="'finished-batch-'+item.key" v-model="item.batch" class="input" maxlength="100"></div><div class="field"><label :for="'external-code-'+item.key">SKU（可选）</label><input :id="'external-code-'+item.key" v-model="item.materialCode" class="input" maxlength="100"></div></template>
        <template v-else><div class="field"><label :for="'external-code-'+item.key">物料编码</label><input :id="'external-code-'+item.key" v-model="item.materialCode" class="input" maxlength="100"></div>
        <div class="grid-2">
          <div class="field"><label :for="'external-production-'+item.key">生产编号</label><input :id="'external-production-'+item.key" v-model="item.productionNumber" class="input" maxlength="100"></div>
          <div class="field"><label :for="'external-batch-'+item.key">物料批次</label><input :id="'external-batch-'+item.key" v-model="item.materialBatch" class="input" maxlength="100"></div>
        </div></template>
        <div class="field"><label :for="'external-quantity-'+item.key">数量（可选）</label><input :id="'external-quantity-'+item.key" v-model.number="item.quantity" class="input" type="number" min="1" max="9007199254740991" step="1" placeholder="留空表示待盘点"></div>
        </template>
      </section>
      <div class="field"><label for="external-stock-notes">备注</label><textarea id="external-stock-notes" v-model="notes" class="textarea" maxlength="2000" placeholder="如：发往第三方的货物，或直接存放在第三方的货物"/></div>
    </fieldset>
    <footer><button class="btn btn-secondary" type="button" :disabled="busy" @click="emit('cancel')">取消</button><button class="btn btn-primary" :disabled="busy">{{busy?'正在保存…':stock?'保存修改':'保存第三方库存'}}</button></footer>
  </form>
</template>
<style scoped>
.external-form,.external-form fieldset{display:grid;gap:14px}.external-form fieldset{border:0;padding:0;margin:0;min-width:0}.external-form header,.external-form footer{display:flex;justify-content:space-between;align-items:center;gap:12px}.external-form h2{margin:0;font-size:21px}.external-form h3{font-size:16px;margin:0}.external-form .hint{margin:0}.number-field{padding:14px;border-left:4px solid var(--blue);background:var(--soft-blue)}.external-item{padding:14px;border:1px solid var(--line);border-radius:9px;background:#f8fafb;display:grid;gap:12px;min-width:0}.external-item .section-head{margin:0}.entry-mode{display:flex;gap:5px;background:#e9eef0;padding:4px;border-radius:8px}.entry-mode button{flex:1;min-height:40px;border:0;border-radius:5px;background:transparent;font-size:13px;cursor:pointer}.entry-mode button[aria-pressed=true]{background:white;color:var(--blue);font-weight:700}.product-results{max-height:200px;overflow:auto;border:1px solid var(--line);border-radius:8px;background:white}.product-results button{width:100%;display:grid;gap:4px;text-align:left;min-height:48px;border:0;border-bottom:1px solid var(--line);padding:10px;background:white;cursor:pointer;overflow-wrap:anywhere}.product-results button:hover{background:var(--soft-blue)}.product-results span{font-size:12px;color:var(--muted)}.product-results>.hint{padding:12px}.selected-product{color:var(--green);font-size:13px;margin:0}.external-form footer{position:sticky;bottom:-20px;padding:14px 0;background:white;border-top:1px solid var(--line);justify-content:flex-end}.external-form button:focus-visible{outline:2px solid var(--blue);outline-offset:2px}@media(max-width:480px){.grid-2{grid-template-columns:minmax(0,1fr)}}
</style>
