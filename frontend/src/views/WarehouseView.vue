<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { productsApi } from '../api';
import { warehouseApi } from '../api/warehouse';
import { errorMessage } from '../api/http';
import { areaLanes, laneLabels, rackOrder, emptyWarehouseFilters, filterWarehousePallets, sortWarehousePallets, selectWarehouseLocation, changeWarehouseScope } from '../utils/warehouse';
import { showDateTime } from '../utils/time';
import type { Product } from '../types';
import type { WarehousePallet, WarehouseOverview } from '../types/warehouse';
import WarehousePalletForm from '../components/WarehousePalletForm.vue';
import WarehouseLocationBoard from '../components/WarehouseLocationBoard.vue';
import WarehousePalletCard from '../components/WarehousePalletCard.vue';
import WarehouseMaterialSummary from '../components/WarehouseMaterialSummary.vue';
import { summarizeWarehouse } from '../utils/warehouseSummary';
import AiAssistant from '../components/AiAssistant.vue';
import { useRoute, useRouter } from 'vue-router';
import { watch } from 'vue';
const route=useRoute(),router=useRouter();
const data=ref<WarehouseOverview>({areas:[],pallets:[]}),products=ref<Product[]>([]),loading=ref(true),busy=ref(false),message=ref(''),notice=ref('');
const filters=ref(emptyWarehouseFilters()),loaded=ref(false);
const areaId=computed(()=>filters.value.areaId),lane=computed({get:()=>filters.value.lane,set:value=>filters.value.lane=value});
const rackData=computed<WarehouseOverview>(()=>{const areas=data.value.areas.filter(a=>a.kind==='RACK'&&rackOrder.includes(a.name)).sort((a,b)=>rackOrder.indexOf(a.name)-rackOrder.indexOf(b.name));const ids=new Set(areas.map(a=>a.id));return {areas,pallets:data.value.pallets.filter(p=>ids.has(p.areaId))}});
const selectedArea=computed(()=>rackData.value.areas.find(a=>a.id===areaId.value));
const racks=computed(()=>rackData.value.areas);
const orderedAreas=computed(()=>racks.value);
const visible=computed(()=>sortWarehousePallets(filterWarehousePallets(rackData.value.pallets,rackData.value.areas,filters.value),rackData.value.areas));
const viewMode=ref<'pallet'|'material'>('pallet');
const materials=computed(()=>summarizeWarehouse(rackData.value.pallets,rackData.value.areas,filters.value));
const detailDialog=ref<HTMLDialogElement>(),detailPallet=ref<WarehousePallet>();
async function openDetail(p:WarehousePallet){detailPallet.value=rackData.value.pallets.find(row=>row.id===p.id)??p;await nextTick();detailDialog.value?.showModal()}
const hasFilters=computed(()=>Boolean(filters.value.query||filters.value.row||areaId.value||filters.value.unknownOnly||filters.value.hideFinished||filters.value.finished!==undefined));
function selectArea(id?:number,l=''){const area=rackData.value.areas.find(a=>a.id===id);if(area)filters.value=selectWarehouseLocation(filters.value,area,l);else clearLocation()}
function clearLocation(){filters.value={...filters.value,kind:'',row:'',areaId:undefined,lane:''}}
function clearFilters(){filters.value=emptyWarehouseFilters()}
function changeScope(scope:Parameters<typeof changeWarehouseScope>[1]){filters.value=changeWarehouseScope(filters.value,scope)}
async function load(){
  loading.value=true;message.value='';
  try{
    data.value=await warehouseApi.overview();loaded.value=true;
    if(areaId.value){
      const area=rackData.value.areas.find(a=>a.id===areaId.value);
      if(area)selectArea(area.id,areaLanes(area).includes(lane.value)?lane.value:'');
      else clearLocation();
    }
  }catch(e){message.value=errorMessage(e)}finally{loading.value=false}
}

const formDialog=ref<HTMLDialogElement>(),formKey=ref(0),editing=ref<WarehousePallet>(),appendMode=ref(false),initialArea=ref<number>(),initialLane=ref('LOWER');
async function openForm(pallet?:WarehousePallet,append=false){
  if(pallet)pallet=rackData.value.pallets.find(row=>row.id===pallet!.id)??pallet;
  if(busy.value)return;busy.value=true;message.value='';
  try{products.value=await productsApi.search('',true)}catch{products.value=[];notice.value='已有产品列表暂时无法读取，仍可手动填写产品名称。'}
  editing.value=pallet;appendMode.value=append;initialArea.value=pallet?.areaId||areaId.value||racks.value[0]?.id;initialLane.value=pallet?.lane||lane.value||'LOWER';formKey.value++;await nextTick();formDialog.value?.showModal();busy.value=false;
}
async function saved(pallet:WarehousePallet,continueEntry=false){
  const index=data.value.pallets.findIndex(p=>p.id===pallet.id);if(index<0)data.value.pallets.push(pallet);else data.value.pallets[index]=pallet;
  notice.value=`已保存 · ${pallet.location}`;if(!continueEntry)formDialog.value?.close();
}
const removeDialog=ref<HTMLDialogElement>(),removing=ref<WarehousePallet>(),removeError=ref('');
async function requestRemove(p:WarehousePallet){if(busy.value)return;removing.value=data.value.pallets.find(row=>row.id===p.id)??p;removeError.value='';await nextTick();removeDialog.value?.showModal()}
async function remove(){
  const p=removing.value;if(busy.value||!p)return;
  busy.value=true;removeError.value='';
  try{await warehouseApi.remove(p.id,p.version);data.value.pallets=data.value.pallets.filter(row=>row.id!==p.id);notice.value=`已移除托盘 · ${p.location}`;removeDialog.value?.close()}
  catch(e){removeError.value=errorMessage(e)}finally{busy.value=false}
}

async function openLinkedPallet(){const id=Number(route.query.palletId);if(!id)return;const p=rackData.value.pallets.find(p=>p.id===id);if(p)await openDetail(p);else notice.value='对应货架托盘已不存在，请刷新后核对。'}
function clearPalletLink(){if(route.path==='/warehouse'&&route.query.palletId){const query={...route.query};delete query.palletId;void router.replace({query})}}
watch(()=>route.query.palletId,openLinkedPallet);
onMounted(async()=>{await load();await openLinkedPallet()});
</script>
<template>
  <div class="page wide warehouse-page">
    <div class="warehouse-heading"><div><p class="eyebrow">固定货架 · 托盘 · 在库物料</p><h1 class="page-title">仓库管理</h1><p class="page-lead">选择货架位置，登记托盘中的产品。</p></div><button class="btn btn-primary" :disabled="loading||busy||!racks.length" @click="openForm()">＋ 录入托盘</button></div>
    <nav class="warehouse-nav" aria-label="仓库管理分类"><RouterLink to="/warehouse" aria-current="page">本地仓库</RouterLink><RouterLink to="/warehouse/third-party">第三方仓库</RouterLink></nav>
    <p v-if="message" class="error-text" role="alert">{{message}} <button class="btn btn-secondary" :disabled="loading" @click="load">重试</button></p>
    <p v-if="notice" class="notice" role="status">{{notice}}<button aria-label="关闭提示" @click="notice=''">×</button></p>
    <div class="warehouse-toolbar">
      <p class="scope-note">记录现场库存，数量留空表示待盘点。</p>
      <div class="toolbar-actions"><button class="btn btn-ghost" :disabled="loading||busy" @click="load">{{loading?'刷新中…':'刷新'}}</button></div>
    </div>
    <WarehouseLocationBoard :data="rackData" :filters="filters" :loading="loading" @select="selectArea" @clear="clearLocation"/>
    <AiAssistant :scope="filters"/>
    <section class="stock-results" aria-labelledby="stock-list-title" :aria-busy="loading">
      <div class="stock-filters card">
        <div class="field search-field"><label for="warehouse-search">查找物料或托盘</label><input id="warehouse-search" v-model="filters.query" name="warehouse-search" autocomplete="off" :spellcheck="false" class="input" type="search" placeholder="产品、物料批次、BATCH、生产编号、SKU 或货位…"></div>
        <div class="field"><label for="warehouse-row">排号</label><select id="warehouse-row" name="warehouse-row" class="select" :value="filters.row" @change="changeScope({row:($event.target as HTMLSelectElement).value})"><option value="">全部排号</option><option v-for="rack in racks" :key="rack.id" :value="rack.name">{{rack.name}} 排</option></select></div>
        <div class="field"><label for="warehouse-material-kind">物料类型</label><select id="warehouse-material-kind" class="select" v-model="filters.finished"><option :value="undefined">全部类型</option><option :value="true">成品</option><option :value="false">非成品</option></select></div>
        <label class="unknown-filter"><input v-model="filters.unknownOnly" name="warehouse-pending" type="checkbox">只看待盘点</label>
      </div>
      <div v-if="hasFilters" class="active-filters" aria-label="当前筛选条件">
        <button v-if="filters.query" @click="filters.query=''" :aria-label="`清除关键词 ${filters.query}`">关键词：{{filters.query}} ×</button>
        <button v-if="filters.row" @click="changeScope({row:''})">{{filters.row}} 排 ×</button>
        <button v-if="selectedArea" @click="filters.areaId=undefined;filters.lane=''">货位：{{selectedArea.name}}{{lane?' · '+laneLabels[lane]:''}} ×</button>
        <button v-if="filters.unknownOnly" @click="filters.unknownOnly=false">只看待盘点 ×</button>
        <button v-if="filters.finished!==undefined" @click="filters.finished=undefined">{{filters.finished?'成品':'非成品'}} ×</button>
        <button class="clear-filters" @click="clearFilters">清除全部筛选</button>
      </div>
      <div class="results-heading"><h2 id="stock-list-title">{{viewMode==='pallet'?'托盘明细':'物料汇总'}} <span role="status">{{loaded?(viewMode==='pallet'?`找到 ${visible.length} 托`:`当前范围 ${materials.length} 种物料`):'等待读取'}}</span></h2><div class="view-switch" role="group" aria-label="查看方式"><button :aria-pressed="viewMode==='pallet'" @click="viewMode='pallet'">按托盘查看</button><button :aria-pressed="viewMode==='material'" @click="viewMode='material'">按物料汇总</button></div></div>
      <p v-if="loading&&!loaded" class="card empty" role="status">正在读取托盘…</p>
      <div v-else-if="!loaded" class="card empty"><strong>暂时无法读取仓库数据</strong><p>请检查网络后重试。</p><button class="btn btn-secondary" :disabled="loading" @click="load">重新加载</button></div>
      <div v-else-if="viewMode==='pallet'?!visible.length:!materials.length" class="card empty"><strong>{{hasFilters?'当前条件下没有托盘':'仓库还没有录入托盘'}}</strong><p>{{hasFilters?'该货位可能尚未录入，或托盘不符合其他筛选条件。':'可直接录入第一个托盘。'}}</p><button v-if="hasFilters" class="btn btn-secondary" @click="clearFilters">清除全部筛选</button></div>
      <WarehouseMaterialSummary v-else-if="viewMode==='material'" :materials="materials"/>
      <div v-else class="pallet-grid"><WarehousePalletCard v-for="p in visible" :key="p.id" :pallet="p" :busy="busy||loading" @detail="openDetail(p)" @append="openForm(p,true)" @edit="openForm(p)" @remove="requestRemove(p)"/></div>
    </section>
    <dialog ref="detailDialog" @close="clearPalletLink" class="warehouse-dialog" aria-labelledby="pallet-detail-title"><header class="dialog-heading"><h2 id="pallet-detail-title">托盘详情</h2><button class="btn btn-secondary" @click="detailDialog?.close()">关闭</button></header><WarehousePalletCard v-if="detailPallet" :pallet="detailPallet" :busy="busy||loading" :interactive="false" @append="detailDialog?.close();openForm(detailPallet,true)" @edit="detailDialog?.close();openForm(detailPallet)" @remove="detailDialog?.close();requestRemove(detailPallet)"/></dialog>
    <dialog ref="removeDialog" class="warehouse-dialog remove-dialog" aria-labelledby="remove-title" aria-describedby="remove-description" @cancel="busy&&$event.preventDefault()">
      <h2 id="remove-title">移除这个托盘？</h2>
      <div v-if="removing" class="remove-summary"><p>{{removing.location}}</p><ul><li v-for="(item,index) in removing.items" :key="index">{{item.productName}} · {{item.quantity==null?'数量未知':`${item.quantity.toLocaleString()} ${item.unit||''}`}}</li></ul></div>
      <p id="remove-description" class="hint">移除后将释放该位置。此操作不会修改生产任务或流转记录。</p>
      <p v-if="removeError" class="error-text" role="alert">{{removeError}}</p>
      <div class="confirm-actions"><button autofocus class="btn btn-secondary" :disabled="busy" @click="removeDialog?.close()">取消</button><button class="btn btn-danger" :disabled="busy" @click="remove">{{busy?'正在移除…':'确认移除托盘'}}</button></div>
    </dialog>

    <dialog ref="formDialog" class="warehouse-dialog" aria-label="托盘录入与编辑" @cancel.prevent><WarehousePalletForm :key="formKey" :areas="orderedAreas" :pallets="rackData.pallets" :products="products" :pallet="editing" :append="appendMode" :initial-area-id="initialArea" :initial-lane="initialLane" @saved="saved" @cancel="formDialog?.close()"/></dialog>
  </div>
</template>
<style scoped>
.warehouse-nav{display:flex;gap:8px;border-bottom:1px solid var(--line);margin:16px 0 22px}.warehouse-nav a{padding:12px 16px;color:var(--muted);text-decoration:none;min-height:44px}.warehouse-nav a[aria-current]{color:var(--blue);border-bottom:3px solid var(--blue);font-weight:700}
.note-more{margin-left:12px;text-decoration:underline}.view-switch{display:flex;flex-shrink:0;border:1px solid var(--line);border-radius:7px;overflow:hidden}.view-switch button{min-height:44px;padding:8px 12px;border:0;background:white;color:var(--blue);font-size:12px;cursor:pointer}.view-switch button[aria-pressed=true]{background:var(--blue);color:white}.view-switch button:hover{box-shadow:inset 0 0 0 2px #9bbaca}.view-switch button:focus-visible{outline:2px solid var(--blue);outline-offset:-4px}.warehouse-dialog{overscroll-behavior:contain}@media(max-width:420px){.results-heading{flex-wrap:wrap}}
.warehouse-heading{display:flex;justify-content:space-between;align-items:center;gap:16px}.warehouse-heading>.btn{flex-shrink:0}.eyebrow{font-size:11px;letter-spacing:.1em;color:var(--muted);margin:0 0 6px}.metrics-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin-top:6px}.metric-card{padding:14px 16px}.metric-card h2{font-size:12px;font-weight:500;margin:0;color:var(--muted)}.metric-card p{display:flex;align-items:baseline;gap:6px;margin:7px 0 4px;color:var(--blue)}.metric-card strong{font:700 28px Consolas,monospace;font-variant-numeric:tabular-nums}.metric-card p span{font-size:12px;color:var(--muted)}.metric-note{font-size:11px;color:var(--muted)}.warehouse-toolbar{display:flex;justify-content:space-between;align-items:flex-start;gap:12px;margin:12px 0}.toolbar-actions{display:flex;gap:6px;flex-wrap:wrap;flex-shrink:0}.toolbar-actions .btn{font-size:12px}.scope-note{font-size:12px;color:var(--muted);max-width:600px}.scope-note summary{cursor:pointer;min-height:44px;align-content:center;color:var(--blue)}.scope-note p{line-height:1.8;margin:0 0 8px}.notice{display:flex;justify-content:space-between;align-items:center;background:var(--soft-green);color:var(--green);padding:8px 12px;font-size:13px;border-radius:8px;overflow-wrap:anywhere}.notice button{border:0;background:none;font-size:22px;min-width:40px;min-height:40px;cursor:pointer}.stock-results{margin-top:18px}.stock-filters{display:grid;grid-template-columns:minmax(200px,1fr) 130px 120px auto;align-items:end;gap:12px;padding:14px}.stock-filters label{font-size:12px}.unknown-filter{display:flex;align-items:center;gap:6px;min-height:44px;white-space:nowrap}.unknown-filter input{width:17px;height:17px;accent-color:var(--blue)}.active-filters{display:flex;flex-wrap:wrap;gap:6px;margin-top:10px}.active-filters button{min-height:36px;background:var(--soft-blue);color:var(--blue);font-size:12px;border:1px solid #cbdce5;border-radius:6px;padding:6px 10px;cursor:pointer;overflow-wrap:anywhere;max-width:100%}.active-filters .clear-filters{border-color:transparent;background:transparent;text-decoration:underline;text-underline-offset:3px}.results-heading{display:flex;justify-content:space-between;align-items:center;gap:10px;margin:16px 0 10px}.results-heading h2{font-size:16px;margin:0}.results-heading h2 span{font-size:12px;font-weight:400;margin-left:8px;color:var(--muted)}.pallet-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.text-button{min-height:40px;border:0;background:none;padding:6px 8px;color:var(--blue);font-size:12px;cursor:pointer}.remove-dialog{max-width:480px}.remove-dialog h2{font-size:20px;margin-top:0}.remove-summary{background:#f5f8fa;border-radius:8px;padding:12px;font-size:13px;overflow-wrap:anywhere}.remove-summary p{color:var(--muted);margin:6px 0}.remove-summary ul{padding-left:20px;margin:8px 0 0;line-height:1.8}.confirm-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:18px}.stock-photo img{display:block;width:112px;height:90px;object-fit:cover;border-radius:7px;border:1px solid var(--line)}
.warehouse-dialog{width:min(680px,calc(100vw - 24px));max-height:90dvh;overflow-y:auto;border:1px solid var(--line);border-radius:14px;padding:20px;color:var(--ink);background:#fff}.warehouse-dialog::backdrop{background:#122d3db3}.dialog-heading{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:16px}.dialog-heading h2{font-size:20px;margin:0}.aisle-setting{display:flex;align-items:center;gap:8px;padding:12px 0;border-bottom:1px solid var(--line)}.aisle-setting>div{flex:1;min-width:0;display:grid;gap:5px;overflow-wrap:anywhere}.aisle-setting span{font-size:12px;color:var(--muted)}.aisle-form{display:grid;gap:12px;margin-top:20px}.aisle-form h3{margin:0;font-size:16px}.history-event{padding:14px 0;border-bottom:1px solid var(--line)}.history-event time{font-size:11px;color:var(--muted)}.history-event h3{font-size:14px;line-height:1.6;margin:5px 0;overflow-wrap:anywhere}.history-event>span{font-size:12px}.history-event details{font-size:12px;margin-top:8px;line-height:1.7;overflow-wrap:anywhere}.history-event summary{min-height:36px;cursor:pointer;color:var(--blue)}.history-event h4{margin:8px 0}.history-event p{margin:4px 0}.history-pages{display:flex;justify-content:space-between;align-items:center;padding-top:16px;font-size:13px}
@media(max-width:900px){.stock-filters{grid-template-columns:minmax(0,1fr) 1fr 1fr}.search-field{grid-column:1/-1}.unknown-filter{justify-content:flex-end}}@media(max-width:650px){.warehouse-heading{align-items:flex-start}.warehouse-heading .btn{font-size:12px;padding:9px}.warehouse-heading .page-lead{max-width:230px}.metrics-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.metric-card{padding:12px}.metric-card strong{font-size:26px}.warehouse-toolbar{flex-wrap:wrap;gap:0}.toolbar-actions{margin-left:auto}.pallet-grid{grid-template-columns:1fr}.stock-filters{gap:10px;padding:12px;grid-template-columns:repeat(2,minmax(0,1fr))}.unknown-filter{grid-column:1/-1;justify-content:flex-start}.warehouse-dialog{padding:16px}.results-heading h2 span{display:block;margin:5px 0 0}.active-filters button{min-height:44px}}
</style>
<style scoped>.metrics-grid{grid-template-columns:repeat(3,minmax(0,1fr))}</style>
