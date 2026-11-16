<script setup lang="ts">
import { ref } from 'vue';
import { thirdPartyWarehouseApi } from '../api/thirdPartyWarehouse';
import { errorMessage } from '../api/http';
import { thirdPartyProductChoices, thirdPartyProductDefaults, productIdentifiers } from '../utils/thirdPartyWarehouse';
import type { Product } from '../types';
import type { ThirdPartyWarehouse, ThirdPartyStock, ThirdPartyStockItem, ThirdPartyStockInput } from '../types/thirdPartyWarehouse';

const props=defineProps<{warehouses:ThirdPartyWarehouse[];products:Product[];stock?:ThirdPartyStock;initialWarehouseId?:number;catalogError?:string}>();
const emit=defineEmits<{saved:[stock:ThirdPartyStock];cancel:[]}>();
type DraftItem=Omit<ThirdPartyStockItem,'quantity'|'cartons'>&{key:number;mode:'catalog'|'manual';query:string;quantity:number|null|'';cartons:number|null|''};
let key=0;
function blank():DraftItem{return {key:key++,mode:'catalog',query:'',...thirdPartyProductDefaults(),quantity:null,cartons:null}}
const warehouseId=ref(props.stock?.warehouseId??props.initialWarehouseId??props.warehouses[0]?.id);
const warehouseNumber=ref(props.stock?.warehouseNumber??''),notes=ref(props.stock?.notes??'');
const items=ref<DraftItem[]>(props.stock?.items.map(i=>({...blank(),...i,mode:i.productId?'catalog':'manual',query:i.productId?i.productName:''}))??[blank()]);
const busy=ref(false),message=ref('');
function product(item:DraftItem){return props.products.find(p=>p.id===item.productId)}
function choices(item:DraftItem){return productIdentifiers(product(item))}
function selectProduct(item:DraftItem,p:Product){Object.assign(item,thirdPartyProductDefaults(p));item.query=p.name}
function changeSearch(item:DraftItem){Object.assign(item,thirdPartyProductDefaults())}
function changeMode(item:DraftItem,mode:DraftItem['mode']){
  if(item.mode===mode)return;
  item.mode=mode;item.query='';Object.assign(item,thirdPartyProductDefaults());
}
async function save(){
  if(busy.value)return;message.value='';
  if(!warehouseId.value||!warehouseNumber.value.trim()){message.value='请选择第三方仓库，并填写对方提供的仓库号。';return}
  if(items.value.some(i=>i.mode==='catalog'?!i.productId:!i.productName.trim())){message.value='请为每行物料选择已有产品，或切换手动录入并填写产品名称。';return}
  const input:ThirdPartyStockInput={
    warehouseId:warehouseId.value,warehouseNumber:warehouseNumber.value.trim(),notes:notes.value.trim(),version:props.stock?.version,
    items:items.value.map(i=>({productId:i.mode==='catalog'?i.productId:null,productName:i.productName.trim(),
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
      <div class="number-field field"><label for="external-stock-number">第三方仓库号（必填）</label><input id="external-stock-number" v-model="warehouseNumber" class="input mono" required maxlength="100" placeholder="填写对方提供的仓库号，保留前导零"><span class="hint">调回货物时使用此号码。同一个仓库号下的货物请添加在同一条记录中。</span></div>
      <div class="section-head"><h3>这个仓库号下的物料</h3><button class="btn btn-secondary" type="button" :disabled="items.length>=50" @click="items.push(blank())">＋ 添加物料</button></div>
      <p class="hint">可搜索产品管理中所有“仓库 → 生产车间”方向的产品；没有建档的货物可手动录入。</p>
      <section v-for="(item,index) in items" :key="item.key" class="external-item">
        <div class="section-head"><strong>物料 {{index+1}}</strong><button v-if="items.length>1" class="btn btn-ghost" type="button" :aria-label="'移除物料 '+(index+1)" @click="items.splice(index,1)">移除</button></div>
        <div class="entry-mode" role="group" :aria-label="'物料 '+(index+1)+' 录入方式'"><button type="button" :aria-pressed="item.mode==='catalog'" @click="changeMode(item,'catalog')">搜索已有产品</button><button type="button" :aria-pressed="item.mode==='manual'" @click="changeMode(item,'manual')">手动录入</button></div>
        <template v-if="item.mode==='catalog'">
          <div class="field"><label :for="'external-search-'+item.key">搜索产品</label><input :id="'external-search-'+item.key" v-model="item.query" class="input" type="search" autocomplete="off" placeholder="产品名称、物料编码、生产编号或物料批次" @input="changeSearch(item)"></div>
          <div v-if="!item.productId" class="product-results" aria-label="产品搜索结果">
            <button v-for="p in thirdPartyProductChoices(products,item.query)" :key="p.id" type="button" @click="selectProduct(item,p)"><strong>{{p.name}}</strong><span>{{p.materialCode}}<template v-if="p.productionNumbers?.length"> · {{p.productionNumbers.join(' / ')}}</template><template v-if="p.materialBatches?.length"> · {{p.materialBatches.join(' / ')}}</template></span></button>
            <p v-if="!thirdPartyProductChoices(products,item.query).length" class="hint">未找到匹配产品，可修改关键词或切换手动录入。</p>
          </div>
          <p v-else class="selected-product" role="status">已选择：{{item.productName}}</p>
        </template>
        <div v-else class="field"><label :for="'external-name-'+item.key">产品名称</label><input :id="'external-name-'+item.key" v-model="item.productName" class="input" required maxlength="200"></div>
        <div class="field"><label :for="'external-code-'+item.key">物料编码</label><input :id="'external-code-'+item.key" v-model="item.materialCode" class="input" maxlength="100" :list="'external-codes-'+item.key" :required="choices(item).codes.length>1"><datalist :id="'external-codes-'+item.key"><option v-for="c in choices(item).codes" :key="c" :value="c"/></datalist><span v-if="choices(item).codes.length>1" class="hint">此产品有多个物料编码，请选择本次实际存放的编码。</span></div>
        <div class="grid-2">
          <div class="field"><label :for="'external-production-'+item.key">生产编号</label><input :id="'external-production-'+item.key" v-model="item.productionNumber" class="input" maxlength="100" :list="'external-numbers-'+item.key" :required="choices(item).numbers.length>1"><datalist :id="'external-numbers-'+item.key"><option v-for="n in choices(item).numbers" :key="n" :value="n"/></datalist><span v-if="choices(item).numbers.length>1" class="hint">有多个生产编号，请选择实际编号。</span></div>
          <div class="field"><label :for="'external-batch-'+item.key">物料批次</label><input :id="'external-batch-'+item.key" v-model="item.materialBatch" class="input" maxlength="100" :list="'external-batches-'+item.key" :required="choices(item).batches.length>1"><datalist :id="'external-batches-'+item.key"><option v-for="b in choices(item).batches" :key="b" :value="b"/></datalist><span v-if="choices(item).batches.length>1" class="hint">有多个物料批次，请选择实际批次。</span></div>
        </div>
        <div class="grid-2"><div class="field"><label :for="'external-quantity-'+item.key">库存数量（可选）</label><input :id="'external-quantity-'+item.key" v-model.number="item.quantity" class="input" type="number" min="1" max="9007199254740991" step="1" placeholder="留空表示待盘点"></div><div class="field"><label :for="'external-unit-'+item.key">单位（可选）</label><input :id="'external-unit-'+item.key" v-model="item.unit" class="input" maxlength="20" placeholder="如：个、条、盒"></div></div>
        <div class="field"><label :for="'external-cartons-'+item.key">库存箱数（可选）</label><input :id="'external-cartons-'+item.key" v-model.number="item.cartons" class="input" type="number" min="0" max="2147483647" step="1"></div>
      </section>
      <div class="field"><label for="external-stock-notes">备注</label><textarea id="external-stock-notes" v-model="notes" class="textarea" maxlength="2000" placeholder="如：发往第三方的货物，或直接存放在第三方的货物"/></div>
    </fieldset>
    <footer><button class="btn btn-secondary" type="button" :disabled="busy" @click="emit('cancel')">取消</button><button class="btn btn-primary" :disabled="busy">{{busy?'正在保存…':stock?'保存修改':'保存第三方库存'}}</button></footer>
  </form>
</template>
<style scoped>
.external-form,.external-form fieldset{display:grid;gap:14px}.external-form fieldset{border:0;padding:0;margin:0;min-width:0}.external-form header,.external-form footer{display:flex;justify-content:space-between;align-items:center;gap:12px}.external-form h2{margin:0;font-size:21px}.external-form h3{font-size:16px;margin:0}.external-form .hint{margin:0}.number-field{padding:14px;border-left:4px solid var(--blue);background:var(--soft-blue)}.external-item{padding:14px;border:1px solid var(--line);border-radius:9px;background:#f8fafb;display:grid;gap:12px;min-width:0}.external-item .section-head{margin:0}.entry-mode{display:flex;gap:5px;background:#e9eef0;padding:4px;border-radius:8px}.entry-mode button{flex:1;min-height:40px;border:0;border-radius:5px;background:transparent;font-size:13px;cursor:pointer}.entry-mode button[aria-pressed=true]{background:white;color:var(--blue);font-weight:700}.product-results{max-height:200px;overflow:auto;border:1px solid var(--line);border-radius:8px;background:white}.product-results button{width:100%;display:grid;gap:4px;text-align:left;min-height:48px;border:0;border-bottom:1px solid var(--line);padding:10px;background:white;cursor:pointer;overflow-wrap:anywhere}.product-results button:hover{background:var(--soft-blue)}.product-results span{font-size:12px;color:var(--muted)}.product-results>.hint{padding:12px}.selected-product{color:var(--green);font-size:13px;margin:0}.external-form footer{position:sticky;bottom:-20px;padding:14px 0;background:white;border-top:1px solid var(--line);justify-content:flex-end}.external-form button:focus-visible{outline:2px solid var(--blue);outline-offset:2px}@media(max-width:480px){.grid-2{grid-template-columns:minmax(0,1fr)}}
</style>
