<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { productsApi } from '../api';
import { thirdPartyWarehouseApi as api } from '../api/thirdPartyWarehouse';
import { errorMessage } from '../api/http';
import { filterThirdPartyStocks } from '../utils/thirdPartyWarehouse';
import { showDateTime } from '../utils/time';
import type { Product } from '../types';
import type { ThirdPartyOverview, ThirdPartyWarehouse, ThirdPartyStock } from '../types/thirdPartyWarehouse';
import ThirdPartyStockForm from '../components/ThirdPartyStockForm.vue';

const data=ref<ThirdPartyOverview>({warehouses:[],stocks:[]}),loading=ref(false),loaded=ref(false),busy=ref(false);
const message=ref(''),notice=ref(''),query=ref(''),warehouseId=ref<number>();
const visible=computed(()=>filterThirdPartyStocks(data.value.stocks,query.value,warehouseId.value));
const selected=computed(()=>data.value.warehouses.find(w=>w.id===warehouseId.value));
async function load(){loading.value=true;message.value='';try{data.value=await api.overview();loaded.value=true}catch(e){message.value=errorMessage(e)}finally{loading.value=false}}
const warehouseDialog=ref<HTMLDialogElement>(),editingWarehouse=ref<ThirdPartyWarehouse>();
const name=ref(''),location=ref(''),warehouseError=ref('');
async function openWarehouse(w?:ThirdPartyWarehouse){editingWarehouse.value=w;name.value=w?.name??'';location.value=w?.location??'';warehouseError.value='';await nextTick();warehouseDialog.value?.showModal()}
async function saveWarehouse(){
  if(busy.value)return;busy.value=true;warehouseError.value='';
  try{
    const input={name:name.value.trim(),location:location.value.trim(),version:editingWarehouse.value?.version};
    const w=editingWarehouse.value?await api.updateWarehouse(editingWarehouse.value.id,input):await api.createWarehouse(input);
    notice.value='已保存第三方仓库：'+w.name;warehouseDialog.value?.close();await load();
  }catch(e){warehouseError.value=errorMessage(e)}finally{busy.value=false}
}
const stockDialog=ref<HTMLDialogElement>(),editingStock=ref<ThirdPartyStock>(),products=ref<Product[]>([]),formKey=ref(0),catalogError=ref('');
async function openStock(stock?:ThirdPartyStock){
  if(busy.value)return;busy.value=true;message.value='';catalogError.value='';
  try{products.value=await productsApi.search('',true,'WAREHOUSE_TO_PRODUCTION')}
  catch{products.value=[];catalogError.value='已有产品暂时无法读取，请关闭后重试；也可以切换为手动录入。'}
  editingStock.value=stock;formKey.value++;await nextTick();stockDialog.value?.showModal();busy.value=false;
}
function saved(stock:ThirdPartyStock){
  const index=data.value.stocks.findIndex(s=>s.id===stock.id);
  if(index<0)data.value.stocks.unshift(stock);else data.value.stocks[index]=stock;
  notice.value='已保存第三方库存 · 仓库号 '+stock.warehouseNumber;stockDialog.value?.close();
}
const removeDialog=ref<HTMLDialogElement>(),removing=ref<{kind:'warehouse';value:ThirdPartyWarehouse}|{kind:'stock';value:ThirdPartyStock}>(),removeError=ref('');
async function requestRemove(target:NonNullable<typeof removing.value>){removing.value=target;removeError.value='';await nextTick();removeDialog.value?.showModal()}
async function remove(){
  if(!removing.value||busy.value)return;busy.value=true;removeError.value='';
  try{
    const target=removing.value;
    if(target.kind==='warehouse'){await api.removeWarehouse(target.value.id,target.value.version);if(warehouseId.value===target.value.id)warehouseId.value=undefined}
    else await api.removeStock(target.value.id,target.value.version);
    notice.value=target.kind==='warehouse'?'已删除第三方仓库':'已移除第三方库存记录';removeDialog.value?.close();await load();
  }catch(e){removeError.value=errorMessage(e)}finally{busy.value=false}
}
onMounted(load);
</script>
<template>
  <div class="page wide external-page">
    <header class="external-heading"><div><p class="eyebrow">仓库管理 / 第三方仓库</p><h1 class="page-title">第三方仓库</h1><p class="page-lead">记录外库存货，按对方的仓库号查找和安排调回。</p></div><div class="actions"><button class="btn btn-secondary" :disabled="busy||loading" @click="openWarehouse()">＋ 新增第三方仓库</button><button class="btn btn-primary" :disabled="busy||loading||!data.warehouses.length" @click="openStock()">＋ 录入第三方库存</button></div></header>
    <nav class="warehouse-nav" aria-label="仓库管理分类"><RouterLink to="/warehouse">本地仓库</RouterLink><RouterLink to="/warehouse/third-party" aria-current="page">第三方仓库</RouterLink></nav>
    <p v-if="message" class="error-text" role="alert">{{message}} <button class="btn btn-secondary" :disabled="loading" @click="load">重试</button></p>
    <p v-if="notice" class="notice" role="status">{{notice}}<button aria-label="关闭提示" @click="notice=''">×</button></p>
    <section aria-labelledby="external-warehouses-title" :aria-busy="loading">
      <div class="section-head"><h2 id="external-warehouses-title">已建档仓库 <span class="hint">{{data.warehouses.length}} 个</span></h2><button class="btn btn-ghost" :disabled="loading||busy" @click="load">{{loading?'刷新中…':'刷新'}}</button></div>
      <p v-if="loading&&!loaded" class="card empty" role="status">正在读取第三方仓库…</p>
      <div v-else-if="loaded&&!data.warehouses.length" class="card empty"><strong>先添加存放货物的第三方仓库</strong><p>填写仓库名称和位置后，即可录入已存放或发往该仓库的货物。</p><button class="btn btn-primary" @click="openWarehouse()">新增第三方仓库</button></div>
      <div class="warehouse-grid"><article v-for="w in data.warehouses" :key="w.id" class="card warehouse-card" :class="{selected:warehouseId===w.id}"><button class="warehouse-select" :aria-pressed="warehouseId===w.id" @click="warehouseId=warehouseId===w.id?undefined:w.id"><strong>{{w.name}}</strong><span>{{w.location}}</span><small>{{data.stocks.filter(s=>s.warehouseId===w.id).length}} 个仓库号 · 点击筛选库存</small></button><div class="actions"><button class="btn btn-ghost" :disabled="busy||loading" :aria-label="'编辑仓库 '+w.name" @click="openWarehouse(w)">编辑</button><button class="btn btn-ghost" :disabled="busy||loading||data.stocks.some(s=>s.warehouseId===w.id)" :aria-label="'删除仓库 '+w.name" @click="requestRemove({kind:'warehouse',value:w})">删除</button></div></article></div>
    </section>
    <section aria-labelledby="external-stock-title" :aria-busy="loading">
      <div class="section-head"><h2 id="external-stock-title">第三方库存 <span class="hint" role="status">{{visible.length}} 条记录</span></h2></div>
      <div class="card external-filters"><div class="field"><label for="external-query">按仓库号或产品查找</label><input id="external-query" v-model="query" class="input" type="search" placeholder="仓库号、产品名称、物料编码、生产编号或物料批次"></div><div class="field"><label for="external-filter-warehouse">第三方仓库</label><select id="external-filter-warehouse" v-model="warehouseId" class="select"><option :value="undefined">全部第三方仓库</option><option v-for="w in data.warehouses" :key="w.id" :value="w.id">{{w.name}}</option></select></div></div>
      <p v-if="selected" class="hint">{{selected.name}} · {{selected.location}}</p>
      <div v-if="loaded&&!visible.length" class="card empty"><strong>{{query||warehouseId?'没有符合条件的库存':'还没有第三方库存记录'}}</strong><p>{{query||warehouseId?'可更换仓库或搜索条件。':'可直接登记已经存放在第三方的货物。'}}</p><button v-if="query||warehouseId" class="btn btn-secondary" @click="query='';warehouseId=undefined">清除筛选</button></div>
      <div class="stock-grid"><article v-for="s in visible" :key="s.id" class="card stock-card"><header><div><span class="number-label">第三方仓库号</span><h3 class="stock-number mono">{{s.warehouseNumber}}</h3><p class="stock-location">{{s.warehouseName}} · {{s.warehouseLocation}}</p></div><button class="btn btn-secondary" :disabled="busy||loading" :aria-label="'编辑仓库号 '+s.warehouseNumber" @click="openStock(s)">编辑</button></header><div v-for="(i,index) in s.items" :key="index" class="stock-item"><strong>{{i.productName}}</strong><dl><div><dt>物料编码</dt><dd class="mono">{{i.materialCode||'未录入'}}</dd></div><div><dt>生产编号</dt><dd class="mono">{{i.productionNumber||'未录入'}}</dd></div><div><dt>物料批次</dt><dd class="mono">{{i.materialBatch||'未录入'}}</dd></div></dl><p class="stock-quantity">{{i.quantity==null?'数量待盘点':i.quantity.toLocaleString()+' '+(i.unit||'（单位未录入）')}}<span v-if="i.cartons!=null"> · {{i.cartons}} 箱</span></p></div><p v-if="s.notes" class="stock-notes">备注：{{s.notes}}</p><footer><time>更新于 {{showDateTime(s.updatedAt)}}</time><button class="btn btn-ghost" :disabled="busy||loading" :aria-label="'移除仓库号 '+s.warehouseNumber" @click="requestRemove({kind:'stock',value:s})">移除记录</button></footer></article></div>
    </section>
    <dialog ref="warehouseDialog" class="external-dialog" aria-labelledby="external-warehouse-form-title" @cancel="busy&&$event.preventDefault()"><form class="warehouse-form" @submit.prevent="saveWarehouse"><header><h2 id="external-warehouse-form-title">{{editingWarehouse?'编辑第三方仓库':'新增第三方仓库'}}</h2><button type="button" class="btn btn-secondary" :disabled="busy" @click="warehouseDialog?.close()">关闭</button></header><p v-if="warehouseError" class="error-text" role="alert">{{warehouseError}}</p><fieldset :disabled="busy"><legend class="sr-only">仓库资料</legend><div class="field"><label for="external-warehouse-name">仓库名称</label><input id="external-warehouse-name" v-model="name" class="input" maxlength="100" required></div><div class="field"><label for="external-warehouse-location">仓库位置</label><textarea id="external-warehouse-location" v-model="location" class="textarea" maxlength="500" required placeholder="填写地址或便于识别的位置"/></div></fieldset><footer><button type="button" class="btn btn-secondary" :disabled="busy" @click="warehouseDialog?.close()">取消</button><button class="btn btn-primary" :disabled="busy">{{busy?'正在保存…':'保存仓库'}}</button></footer></form></dialog>
    <dialog ref="stockDialog" class="external-dialog" aria-label="第三方库存录入与编辑" @cancel.prevent><ThirdPartyStockForm :key="formKey" :warehouses="data.warehouses" :products="products" :stock="editingStock" :initial-warehouse-id="warehouseId" :catalog-error="catalogError" @saved="saved" @cancel="stockDialog?.close()"/></dialog>
    <dialog ref="removeDialog" class="external-dialog remove-dialog" aria-labelledby="external-remove-title" @cancel="busy&&$event.preventDefault()"><h2 id="external-remove-title">{{removing?.kind==='warehouse'?'删除第三方仓库？':'移除第三方库存记录？'}}</h2><template v-if="removing"><p>{{removing.kind==='warehouse'?removing.value.name:removing.value.warehouseName+' · 仓库号 '+removing.value.warehouseNumber}}</p><p class="hint">{{removing.kind==='warehouse'?'仅可删除没有库存记录的仓库。':'请先核对货物已调回或已不在第三方仓库。此操作仅移除库存台账，调回后的本地货位请另行录入。'}}操作历史会保留。</p></template><p v-if="removeError" class="error-text" role="alert">{{removeError}}</p><div class="actions"><button class="btn btn-secondary" :disabled="busy" @click="removeDialog?.close()">取消</button><button class="btn btn-danger" :disabled="busy" @click="remove">{{busy?'正在处理…':'确认移除'}}</button></div></dialog>
  </div>
</template>
<style scoped>
.external-heading,.actions,.stock-card header,.stock-card footer,.warehouse-form header,.warehouse-form footer{display:flex;justify-content:space-between;align-items:center;gap:10px}.external-heading{gap:20px}.actions{justify-content:flex-end;flex-wrap:wrap}.eyebrow{font-size:12px;color:var(--muted);margin:0 0 6px}.warehouse-nav{display:flex;gap:8px;border-bottom:1px solid var(--line);margin:16px 0 22px}.warehouse-nav a{padding:12px 16px;color:var(--muted);text-decoration:none;min-height:44px}.warehouse-nav a[aria-current]{color:var(--blue);border-bottom:3px solid var(--blue);font-weight:700}.external-page h2{font-size:18px}.notice{display:flex;justify-content:space-between;align-items:center;background:var(--soft-green);color:var(--green);padding:8px 12px;border-radius:8px;font-size:13px}.notice button{border:0;background:none;font-size:22px;min-width:44px;min-height:44px;cursor:pointer}.warehouse-grid,.stock-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.warehouse-card{padding:14px;min-width:0}.warehouse-card.selected{border-color:var(--blue);background:var(--soft-blue)}.warehouse-select{display:grid;gap:8px;width:100%;text-align:left;background:none;border:0;padding:0;cursor:pointer;color:var(--ink);overflow-wrap:anywhere}.warehouse-select strong{font-size:17px}.warehouse-select span,.warehouse-select small{font-size:12px;color:var(--muted);line-height:1.6}.warehouse-card .actions{margin-top:6px}.external-filters{display:grid;grid-template-columns:minmax(0,1fr) minmax(160px,.4fr);gap:12px;padding:14px;margin-bottom:16px}.external-page>section+section{margin-top:26px}.stock-card{padding:18px;min-width:0;overflow-wrap:anywhere}.stock-card header{align-items:flex-start;border-bottom:1px solid var(--line);padding-bottom:14px}.number-label{font-size:11px;color:var(--muted)}.stock-number{color:var(--blue);font-size:25px;margin:6px 0;white-space:pre-wrap}.stock-location{color:var(--muted);font-size:12px;margin:6px 0 0;line-height:1.7}.stock-item{padding:14px 0;border-bottom:1px solid var(--line)}.stock-item>strong{font-size:15px}.stock-item dl{display:grid;gap:7px;font-size:12px;margin:12px 0}.stock-item dl>div{display:grid;grid-template-columns:76px minmax(0,1fr);gap:8px}.stock-item dt{color:var(--muted)}.stock-item dd{margin:0}.stock-quantity{font-size:13px;font-weight:700;color:var(--blue);margin:8px 0 0}.stock-notes{font-size:12px;line-height:1.7}.stock-card footer{padding-top:8px}.stock-card time{font-size:11px;color:var(--muted)}.external-dialog{width:min(680px,calc(100vw - 24px));max-height:90dvh;overflow:auto;overscroll-behavior:contain;border:1px solid var(--line);border-radius:14px;padding:20px;color:var(--ink);background:white}.external-dialog::backdrop{background:#122d3db3}.external-dialog h2{font-size:20px;margin:0}.warehouse-form,.warehouse-form fieldset{display:grid;gap:16px}.warehouse-form fieldset{border:0;padding:0;margin:0;min-width:0}.warehouse-form footer{justify-content:flex-end}.remove-dialog{max-width:480px}.remove-dialog .hint{line-height:1.8}.external-page button:focus-visible{outline:2px solid var(--blue);outline-offset:2px}@media(max-width:650px){.external-heading{align-items:flex-start;flex-direction:column}.warehouse-grid,.stock-grid,.external-filters{grid-template-columns:minmax(0,1fr)}.external-heading>.actions{justify-content:flex-start}.external-dialog{padding:16px}.stock-card{padding:14px}}
</style>
