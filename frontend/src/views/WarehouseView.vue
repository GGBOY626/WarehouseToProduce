<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { useAuth } from '../auth';
import { productsApi } from '../api';
import { warehouseApi } from '../api/warehouse';
import { errorMessage } from '../api/http';
import { areaLanes, laneLabels, emptyWarehouseFilters, filterWarehousePallets, warehouseStats, selectWarehouseLocation, changeWarehouseScope } from '../utils/warehouse';
import { showDateTime } from '../utils/time';
import type { Product } from '../types';
import type { WarehouseArea, WarehousePallet, WarehouseOverview, WarehouseHistory } from '../types/warehouse';
import WarehousePalletForm from '../components/WarehousePalletForm.vue';
import WarehouseLocationBoard from '../components/WarehouseLocationBoard.vue';
import WarehousePalletCard from '../components/WarehousePalletCard.vue';
import WarehouseMaterialSummary from '../components/WarehouseMaterialSummary.vue';
import { summarizeWarehouse } from '../utils/warehouseSummary';
import AiAssistant from '../components/AiAssistant.vue';
import { useRoute, useRouter } from 'vue-router';
import { watch } from 'vue';
const route=useRoute(),router=useRouter();
const {authenticated}=useAuth();
const data=ref<WarehouseOverview>({areas:[],pallets:[]}),products=ref<Product[]>([]),loading=ref(true),busy=ref(false),message=ref(''),notice=ref('');
const filters=ref(emptyWarehouseFilters()),loaded=ref(false);
const areaId=computed(()=>filters.value.areaId),lane=computed({get:()=>filters.value.lane,set:value=>filters.value.lane=value});
const selectedArea=computed(()=>data.value.areas.find(a=>a.id===areaId.value));
const racks=computed(()=>data.value.areas.filter(a=>a.kind==='RACK'));
const aisles=computed(()=>data.value.areas.filter(a=>a.kind==='AISLE'));
const visible=computed(()=>filterWarehousePallets(data.value.pallets,data.value.areas,filters.value).sort((a,b)=>a.areaId-b.areaId||areaLanes(data.value.areas.find(area=>area.id===a.areaId)).indexOf(a.lane)-areaLanes(data.value.areas.find(area=>area.id===b.areaId)).indexOf(b.lane)||a.position-b.position));
const viewMode=ref<'pallet'|'material'>('pallet');
const materials=computed(()=>summarizeWarehouse(data.value.pallets,data.value.areas,filters.value));
const detailDialog=ref<HTMLDialogElement>(),detailPallet=ref<WarehousePallet>();
async function openDetail(p:WarehousePallet){detailPallet.value=p;await nextTick();detailDialog.value?.showModal()}
const stats=computed(()=>warehouseStats(data.value.pallets));
const metrics=computed(()=>[{label:'已录入托盘总数',value:stats.value.total,note:'所有位置'}, {label:'货架内托盘数',value:stats.value.rack,note:'AA–MM 货架'}, {label:'过道内托盘数',value:stats.value.aisle,note:'全部过道'}, {label:'待盘点托盘数',value:stats.value.pending,note:'含数量未确认的物料'}]);
const hasFilters=computed(()=>Boolean(filters.value.query||filters.value.kind||filters.value.row||areaId.value||filters.value.unknownOnly||filters.value.hideFinished));
function count(id:number,l?:string){return data.value.pallets.filter(p=>p.areaId===id&&(!l||p.lane===l)).length}
function selectArea(id?:number,l=''){const area=data.value.areas.find(a=>a.id===id);if(area)filters.value=selectWarehouseLocation(filters.value,area,l);else clearLocation()}
function clearLocation(){filters.value={...filters.value,kind:'',row:'',areaId:undefined,lane:''}}
function clearFilters(){filters.value=emptyWarehouseFilters()}
function changeScope(scope:Parameters<typeof changeWarehouseScope>[1]){filters.value=changeWarehouseScope(filters.value,scope)}
async function load(){
  loading.value=true;message.value='';
  try{
    data.value=await warehouseApi.overview();loaded.value=true;
    if(areaId.value){
      const area=data.value.areas.find(a=>a.id===areaId.value);
      if(area)selectArea(area.id,areaLanes(area).includes(lane.value)?lane.value:'');
      else clearLocation();
    }
  }catch(e){message.value=errorMessage(e)}finally{loading.value=false}
}

const formDialog=ref<HTMLDialogElement>(),formKey=ref(0),editing=ref<WarehousePallet>(),initialArea=ref<number>(),initialLane=ref('LOWER'),continueEntry=ref(false);
async function openForm(pallet?:WarehousePallet){
  if(busy.value)return;busy.value=true;message.value='';continueEntry.value=false;
  try{products.value=await productsApi.search('',true)}catch{products.value=[];notice.value='已有产品列表暂时无法读取，仍可手动填写产品名称。'}
  editing.value=pallet;initialArea.value=pallet?.areaId||areaId.value||racks.value[0]?.id;initialLane.value=pallet?.lane||lane.value||(selectedArea.value?.kind==='AISLE'?'LEFT_1':'LOWER');formKey.value++;await nextTick();formDialog.value?.showModal();busy.value=false;
}
async function saved(pallet:WarehousePallet,another:boolean){
  const index=data.value.pallets.findIndex(p=>p.id===pallet.id);if(index<0)data.value.pallets.push(pallet);else data.value.pallets[index]=pallet;
  notice.value=`已保存 ${pallet.code} · ${pallet.location}`;
  if(another){initialArea.value=pallet.areaId;initialLane.value=pallet.lane;continueEntry.value=true;editing.value=undefined;formKey.value++;await nextTick();formDialog.value?.scrollTo({top:0});formDialog.value?.querySelector<HTMLInputElement>('input')?.focus()}else formDialog.value?.close();
}
const removeDialog=ref<HTMLDialogElement>(),removing=ref<WarehousePallet>(),removeError=ref('');
async function requestRemove(p:WarehousePallet){if(busy.value)return;removing.value=p;removeError.value='';await nextTick();removeDialog.value?.showModal()}
async function remove(){
  const p=removing.value;if(busy.value||!p)return;
  busy.value=true;removeError.value='';
  try{await warehouseApi.remove(p.id,p.version);data.value.pallets=data.value.pallets.filter(row=>row.id!==p.id);notice.value=`已移除 ${p.code}`;removeDialog.value?.close()}
  catch(e){removeError.value=errorMessage(e)}finally{busy.value=false}
}

const aisleDialog=ref<HTMLDialogElement>(),aisleEditing=ref<WarehouseArea>(),aisleName=ref(''),aisleLanes=ref(2),aisleError=ref(''),aisleRackRow=ref('');
function resetAisle(){aisleEditing.value=undefined;aisleName.value='';aisleLanes.value=2;aisleRackRow.value=''}
async function openAisles(){resetAisle();aisleError.value='';await nextTick();aisleDialog.value?.showModal()}
function editAisle(a:WarehouseArea){aisleEditing.value=a;aisleName.value=a.name;aisleLanes.value=a.lanes;aisleRackRow.value=a.rackRow||'';aisleError.value=''}
async function saveAisle(){
  if(busy.value)return;busy.value=true;aisleError.value='';
  try{const fields={name:aisleName.value,lanes:aisleLanes.value,rackRow:aisleRackRow.value||null};const a=aisleEditing.value?await warehouseApi.updateAisle(aisleEditing.value.id,{...fields,version:aisleEditing.value.version}):await warehouseApi.createAisle(fields);await load();resetAisle();notice.value=`已保存过道 ${a.name}`;if(selectedArea.value&&!areaLanes(selectedArea.value).includes(lane.value))lane.value=''}catch(e){aisleError.value=errorMessage(e)}finally{busy.value=false}
}
async function removeAisle(a:WarehouseArea){
  if(busy.value||!window.confirm(`确定删除空过道“${a.name}”？`))return;busy.value=true;aisleError.value='';
  try{await warehouseApi.removeAisle(a.id,a.version);data.value.areas=data.value.areas.filter(row=>row.id!==a.id);if(areaId.value===a.id)selectArea();if(aisleEditing.value?.id===a.id)resetAisle();notice.value=`已删除空过道 ${a.name}`}catch(e){aisleError.value=errorMessage(e)}finally{busy.value=false}
}

const historyDialog=ref<HTMLDialogElement>(),history=ref<WarehouseHistory>(),historyPage=ref(0),historyId=ref<number>(),historyBusy=ref(false),historyError=ref('');
async function openHistory(id?:number){historyId.value=id;historyPage.value=0;history.value=undefined;await nextTick();historyDialog.value?.showModal();await loadHistory()}
async function loadHistory(){historyBusy.value=true;historyError.value='';try{history.value=await warehouseApi.history(historyPage.value,historyId.value)}catch(e){historyError.value=errorMessage(e)}finally{historyBusy.value=false}}
function historyState(value?:string):WarehousePallet|undefined{if(!value)return;try{return JSON.parse(value)}catch{return undefined}}
async function openLinkedPallet(){const id=Number(route.query.palletId);if(!id)return;const p=data.value.pallets.find(p=>p.id===id);if(p)await openDetail(p);else notice.value='对应托盘已不存在，请刷新后核对。'}
function clearPalletLink(){if(route.path==='/warehouse'&&route.query.palletId){const query={...route.query};delete query.palletId;void router.replace({query})}}
watch(()=>route.query.palletId,openLinkedPallet);
onMounted(async()=>{await load();await openLinkedPallet()});
</script>
<template>
  <div class="page wide warehouse-page">
    <div class="warehouse-heading"><div><p class="eyebrow">货位 · 托盘 · 在库物料</p><h1 class="page-title">仓库管理</h1><p class="page-lead">先找到位置，再记录这托货。A–M 排货架与过道统一查询。</p></div><button v-if="authenticated" class="btn btn-primary" :disabled="loading||busy||!data.areas.length" @click="openForm()">＋ 录入托盘</button><RouterLink v-else class="btn btn-secondary" to="/login?redirect=/warehouse">登录后录入</RouterLink></div>
    <p v-if="message" class="error-text" role="alert">{{message}} <button class="btn btn-secondary" :disabled="loading" @click="load">重试</button></p>
    <p v-if="notice" class="notice" role="status">{{notice}}<button aria-label="关闭提示" @click="notice=''">×</button></p>
    <div class="metrics-grid" :aria-busy="loading" aria-label="仓库托盘统计">
      <article v-for="metric in metrics" :key="metric.label" class="metric-card card"><h2>{{metric.label}}</h2><p><strong>{{loaded?metric.value.toLocaleString():'—'}}</strong><span>托</span></p><span class="metric-note">{{metric.note}}</span></article>
    </div>
    <div class="warehouse-toolbar">
      <details class="scope-note"><summary>记录现场库存，数量留空表示待盘点。<span class="note-more">查看详情</span></summary><p>这里记录现场在库情况。录入、移位、移除托盘不会自动修改生产任务或流转记录；数量留空表示待盘点，不算作零库存。待盘点是独立统计维度，已包含在托盘总数中。一个托盘可包含多项物料，数量和箱数不自动换算。</p></details>
      <div class="toolbar-actions"><button class="btn btn-ghost" :disabled="loading||busy" @click="load">{{loading?'刷新中…':'刷新'}}</button><button class="btn btn-ghost" @click="openHistory()">操作历史</button><button v-if="authenticated" class="btn btn-ghost" :disabled="busy||loading" @click="openAisles">管理过道</button></div>
    </div>
    <WarehouseLocationBoard :data="data" :filters="filters" :loading="loading" @select="selectArea" @clear="clearLocation"/>
    <AiAssistant :scope="filters"/>
    <section class="stock-results" aria-labelledby="stock-list-title" :aria-busy="loading">
      <div class="stock-filters card">
        <div class="field search-field"><label for="warehouse-search">查找物料或托盘</label><input id="warehouse-search" v-model="filters.query" name="warehouse-search" autocomplete="off" :spellcheck="false" class="input" type="search" placeholder="产品、原料批号、批号、托盘号或货位…"></div>
        <div class="field"><label for="warehouse-kind">位置类型</label><select id="warehouse-kind" name="warehouse-kind" class="select" :value="filters.kind" @change="changeScope({kind:($event.target as HTMLSelectElement).value as ''|'RACK'|'AISLE'})"><option value="">全部位置</option><option value="RACK">货架</option><option value="AISLE">过道</option></select></div>
        <div class="field"><label for="warehouse-row">排号</label><select id="warehouse-row" name="warehouse-row" class="select" :value="filters.row" @change="changeScope({row:($event.target as HTMLSelectElement).value})"><option value="">全部排号</option><option v-for="rack in racks" :key="rack.id" :value="rack.name">{{rack.name}} 排</option></select></div>
        <label class="unknown-filter"><input v-model="filters.unknownOnly" name="warehouse-pending" type="checkbox">只看待盘点</label><label class="unknown-filter"><input v-model="filters.hideFinished" name="warehouse-hide-finished" type="checkbox">隐藏成品</label>
      </div>
      <div v-if="hasFilters" class="active-filters" aria-label="当前筛选条件">
        <button v-if="filters.query" @click="filters.query=''" :aria-label="`清除关键词 ${filters.query}`">关键词：{{filters.query}} ×</button>
        <button v-if="filters.kind" @click="changeScope({kind:''})">类型：{{filters.kind==='RACK'?'货架':'过道'}} ×</button>
        <button v-if="filters.row" @click="changeScope({row:''})">{{filters.row}} 排 ×</button>
        <button v-if="selectedArea" @click="filters.areaId=undefined;filters.lane=''">货位：{{selectedArea.name}}{{lane?' · '+laneLabels[lane]:''}} ×</button>
        <button v-if="filters.unknownOnly" @click="filters.unknownOnly=false">只看待盘点 ×</button>
        <button v-if="filters.hideFinished" @click="filters.hideFinished=false">隐藏成品 ×</button>
        <button class="clear-filters" @click="clearFilters">清除全部筛选</button>
      </div>
      <div class="results-heading"><h2 id="stock-list-title">{{viewMode==='pallet'?'托盘明细':'物料汇总'}} <span role="status">{{loaded?(viewMode==='pallet'?`找到 ${visible.length} 托 / 共 ${stats.total} 托`:`当前范围 ${materials.length} 种物料`):'等待读取'}}</span></h2><div class="view-switch" role="group" aria-label="查看方式"><button :aria-pressed="viewMode==='pallet'" @click="viewMode='pallet'">按托盘查看</button><button :aria-pressed="viewMode==='material'" @click="viewMode='material'">按物料汇总</button></div></div>
      <p v-if="loading&&!loaded" class="card empty" role="status">正在读取托盘…</p>
      <div v-else-if="!loaded" class="card empty"><strong>暂时无法读取仓库数据</strong><p>请检查网络后重试。</p><button class="btn btn-secondary" :disabled="loading" @click="load">重新加载</button></div>
      <div v-else-if="viewMode==='pallet'?!visible.length:!materials.length" class="card empty"><strong>{{hasFilters?'当前条件下没有托盘':'仓库还没有录入托盘'}}</strong><p>{{hasFilters?'该货位可能尚未录入，或托盘不符合其他筛选条件。':'登录后，从第一个实际货位开始录入。'}}</p><button v-if="hasFilters" class="btn btn-secondary" @click="clearFilters">清除全部筛选</button></div>
      <WarehouseMaterialSummary v-else-if="viewMode==='material'" :materials="materials"/>
      <div v-else class="pallet-grid"><WarehousePalletCard v-for="p in visible" :key="p.id" :pallet="p" :authenticated="authenticated" :busy="busy||loading" @detail="openDetail(p)" @history="openHistory(p.id)" @edit="openForm(p)" @remove="requestRemove(p)"/></div>
    </section>
    <dialog ref="detailDialog" @close="clearPalletLink" class="warehouse-dialog" aria-labelledby="pallet-detail-title"><header class="dialog-heading"><h2 id="pallet-detail-title">托盘详情</h2><button class="btn btn-secondary" @click="detailDialog?.close()">关闭</button></header><WarehousePalletCard v-if="detailPallet" :pallet="detailPallet" :authenticated="authenticated" :busy="busy||loading" :interactive="false" @history="openHistory(detailPallet.id)" @edit="detailDialog?.close();openForm(detailPallet)" @remove="detailDialog?.close();requestRemove(detailPallet)"/></dialog>
    <dialog ref="removeDialog" class="warehouse-dialog remove-dialog" aria-labelledby="remove-title" aria-describedby="remove-description" @cancel="busy&&$event.preventDefault()">
      <h2 id="remove-title">移除这个托盘？</h2>
      <div v-if="removing" class="remove-summary"><strong class="mono">{{removing.code}}</strong><p>{{removing.location}}</p><ul><li v-for="(item,index) in removing.items" :key="index">{{item.productName}} · {{item.quantity==null?'数量未确认':`${item.quantity.toLocaleString()} ${item.unit||''}`}}</li></ul></div>
      <p id="remove-description" class="hint">移除后将释放该位置，操作历史会保留。此操作不会修改生产任务或流转记录。</p>
      <p v-if="removeError" class="error-text" role="alert">{{removeError}}</p>
      <div class="confirm-actions"><button autofocus class="btn btn-secondary" :disabled="busy" @click="removeDialog?.close()">取消</button><button class="btn btn-danger" :disabled="busy" @click="remove">{{busy?'正在移除…':'确认移除托盘'}}</button></div>
    </dialog>

    <dialog ref="formDialog" class="warehouse-dialog" aria-label="托盘录入与编辑" @cancel.prevent><WarehousePalletForm v-if="authenticated" :key="formKey" :areas="data.areas" :pallets="data.pallets" :products="products" :pallet="editing" :initial-area-id="initialArea" :initial-lane="initialLane" :continue-entry="continueEntry" @saved="saved" @cancel="formDialog?.close()"/></dialog>
    <dialog ref="aisleDialog" class="warehouse-dialog" aria-labelledby="aisle-dialog-title" @cancel="busy&&$event.preventDefault()"><header class="dialog-heading"><div><h2 id="aisle-dialog-title">过道设置</h2><p class="hint">按现场命名，例如 A–B 之间、收货区过道。</p></div><button class="btn btn-secondary" :disabled="busy" @click="aisleDialog?.close()">关闭</button></header><p v-if="aisleError" class="error-text" role="alert">{{aisleError}}</p><div v-for="a in aisles" :key="a.id" class="aisle-setting"><div><strong>{{a.rackRow ? `${a.rackRow} 排旁 · ` : ''}}{{a.name}}</strong><span>{{a.lanes}} 排 · {{count(a.id)}} 托</span></div><button class="btn btn-secondary" :disabled="busy" @click="editAisle(a)">编辑</button><button class="btn btn-danger" :disabled="busy||count(a.id)>0" @click="removeAisle(a)">删除</button></div><form class="aisle-form" @submit.prevent="saveAisle"><h3>{{aisleEditing?'编辑过道':'新增过道'}}</h3><div class="field"><label for="aisle-name">过道名称</label><input id="aisle-name" v-model="aisleName" class="input" required maxlength="60" :disabled="busy"></div><div class="field"><label for="aisle-rack">位于哪一排旁（可选）</label><select id="aisle-rack" v-model="aisleRackRow" class="select" :disabled="busy" @change="aisleRackRow==='A'&&(aisleLanes=4)"><option value="">独立过道</option><option v-for="r in racks" :key="r.id" :value="r.name">{{r.name}} 排旁</option></select></div><div class="field"><label for="aisle-lanes">可放排数</label><select id="aisle-lanes" v-model="aisleLanes" class="select" :disabled="busy||aisleRackRow==='A'"><option :value="2">两排：左一、右一</option><option :value="4">四排：左一、左二、右一、右二</option></select></div><p class="hint">第二排有托盘时不能缩为两排；过道有托盘时不能删除。</p><div class="toolbar-actions"><button v-if="aisleEditing" type="button" class="btn btn-secondary" :disabled="busy" @click="resetAisle">取消编辑</button><button class="btn btn-primary" :disabled="busy">{{busy?'正在保存…':'保存过道'}}</button></div></form></dialog>
    <dialog ref="historyDialog" class="warehouse-dialog" aria-labelledby="warehouse-history-title"><header class="dialog-heading"><h2 id="warehouse-history-title">{{historyId?'托盘操作历史':'仓库操作历史'}}</h2><button class="btn btn-secondary" @click="historyDialog?.close()">关闭</button></header><p v-if="historyError" class="error-text" role="alert">{{historyError}} <button class="text-button" @click="loadHistory">重试</button></p><p v-if="historyBusy" role="status">正在读取…</p><template v-else><p v-if="!historyError&&!history?.content.length" class="empty">暂无操作历史。</p><article v-for="event in history?.content" :key="event.id" class="history-event"><time>{{showDateTime(event.createdAt)}}</time><h3>{{event.description}}</h3><span v-if="event.palletCode" class="mono">{{event.palletCode}}</span><details v-if="event.beforeState||event.afterState"><summary>查看变更前后物料</summary><div v-for="(state,index) in [historyState(event.beforeState),historyState(event.afterState)]" :key="index"><template v-if="state"><h4>{{index===0?'变更前':'变更后'}} · {{state.location}}</h4><p v-for="(item,n) in state.items" :key="n">{{item.productName}}<template v-if="item.materialCode"> · {{item.materialCode}}</template> · 原料批号 {{item.rawMaterialBatch||'未录入'}} · 批号 {{item.batch||'未录入'}} · {{item.quantity==null?'待盘点':`${item.quantity.toLocaleString()} ${item.unit||''}`}}<template v-if="item.cartons!=null"> · {{item.cartons}} 箱</template><a v-if="item.photoUrl" :href="item.photoUrl" target="_blank" rel="noopener" class="stock-photo"><img :src="item.photoUrl" :alt="`${item.productName}照片`" loading="lazy" width="112" height="90"></a></p><p v-if="state.notes">备注：{{state.notes}}</p></template></div></details></article></template><div v-if="history&&history.totalPages>1" class="history-pages"><button class="btn btn-secondary" :disabled="historyBusy||historyPage===0" @click="historyPage--;loadHistory()">上一页</button><span>{{historyPage+1}} / {{history.totalPages}}</span><button class="btn btn-secondary" :disabled="historyBusy||historyPage+1>=history.totalPages" @click="historyPage++;loadHistory()">下一页</button></div></dialog>
  </div>
</template>
<style scoped>
.note-more{margin-left:12px;text-decoration:underline}.view-switch{display:flex;flex-shrink:0;border:1px solid var(--line);border-radius:7px;overflow:hidden}.view-switch button{min-height:44px;padding:8px 12px;border:0;background:white;color:var(--blue);font-size:12px;cursor:pointer}.view-switch button[aria-pressed=true]{background:var(--blue);color:white}.view-switch button:hover{box-shadow:inset 0 0 0 2px #9bbaca}.view-switch button:focus-visible{outline:2px solid var(--blue);outline-offset:-4px}.warehouse-dialog{overscroll-behavior:contain}@media(max-width:420px){.results-heading{flex-wrap:wrap}}
.warehouse-heading{display:flex;justify-content:space-between;align-items:center;gap:16px}.warehouse-heading>.btn{flex-shrink:0}.eyebrow{font-size:11px;letter-spacing:.1em;color:var(--muted);margin:0 0 6px}.metrics-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin-top:6px}.metric-card{padding:14px 16px}.metric-card h2{font-size:12px;font-weight:500;margin:0;color:var(--muted)}.metric-card p{display:flex;align-items:baseline;gap:6px;margin:7px 0 4px;color:var(--blue)}.metric-card strong{font:700 28px Consolas,monospace;font-variant-numeric:tabular-nums}.metric-card p span{font-size:12px;color:var(--muted)}.metric-note{font-size:11px;color:var(--muted)}.warehouse-toolbar{display:flex;justify-content:space-between;align-items:flex-start;gap:12px;margin:12px 0}.toolbar-actions{display:flex;gap:6px;flex-wrap:wrap;flex-shrink:0}.toolbar-actions .btn{font-size:12px}.scope-note{font-size:12px;color:var(--muted);max-width:600px}.scope-note summary{cursor:pointer;min-height:44px;align-content:center;color:var(--blue)}.scope-note p{line-height:1.8;margin:0 0 8px}.notice{display:flex;justify-content:space-between;align-items:center;background:var(--soft-green);color:var(--green);padding:8px 12px;font-size:13px;border-radius:8px;overflow-wrap:anywhere}.notice button{border:0;background:none;font-size:22px;min-width:40px;min-height:40px;cursor:pointer}.stock-results{margin-top:18px}.stock-filters{display:grid;grid-template-columns:minmax(200px,1fr) 130px 120px auto;align-items:end;gap:12px;padding:14px}.stock-filters label{font-size:12px}.unknown-filter{display:flex;align-items:center;gap:6px;min-height:44px;white-space:nowrap}.unknown-filter input{width:17px;height:17px;accent-color:var(--blue)}.active-filters{display:flex;flex-wrap:wrap;gap:6px;margin-top:10px}.active-filters button{min-height:36px;background:var(--soft-blue);color:var(--blue);font-size:12px;border:1px solid #cbdce5;border-radius:6px;padding:6px 10px;cursor:pointer;overflow-wrap:anywhere;max-width:100%}.active-filters .clear-filters{border-color:transparent;background:transparent;text-decoration:underline;text-underline-offset:3px}.results-heading{display:flex;justify-content:space-between;align-items:center;gap:10px;margin:16px 0 10px}.results-heading h2{font-size:16px;margin:0}.results-heading h2 span{font-size:12px;font-weight:400;margin-left:8px;color:var(--muted)}.pallet-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.text-button{min-height:40px;border:0;background:none;padding:6px 8px;color:var(--blue);font-size:12px;cursor:pointer}.remove-dialog{max-width:480px}.remove-dialog h2{font-size:20px;margin-top:0}.remove-summary{background:#f5f8fa;border-radius:8px;padding:12px;font-size:13px;overflow-wrap:anywhere}.remove-summary p{color:var(--muted);margin:6px 0}.remove-summary ul{padding-left:20px;margin:8px 0 0;line-height:1.8}.confirm-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:18px}.stock-photo img{display:block;width:112px;height:90px;object-fit:cover;border-radius:7px;border:1px solid var(--line)}
.warehouse-dialog{width:min(680px,calc(100vw - 24px));max-height:90dvh;overflow-y:auto;border:1px solid var(--line);border-radius:14px;padding:20px;color:var(--ink);background:#fff}.warehouse-dialog::backdrop{background:#122d3db3}.dialog-heading{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:16px}.dialog-heading h2{font-size:20px;margin:0}.aisle-setting{display:flex;align-items:center;gap:8px;padding:12px 0;border-bottom:1px solid var(--line)}.aisle-setting>div{flex:1;min-width:0;display:grid;gap:5px;overflow-wrap:anywhere}.aisle-setting span{font-size:12px;color:var(--muted)}.aisle-form{display:grid;gap:12px;margin-top:20px}.aisle-form h3{margin:0;font-size:16px}.history-event{padding:14px 0;border-bottom:1px solid var(--line)}.history-event time{font-size:11px;color:var(--muted)}.history-event h3{font-size:14px;line-height:1.6;margin:5px 0;overflow-wrap:anywhere}.history-event>span{font-size:12px}.history-event details{font-size:12px;margin-top:8px;line-height:1.7;overflow-wrap:anywhere}.history-event summary{min-height:36px;cursor:pointer;color:var(--blue)}.history-event h4{margin:8px 0}.history-event p{margin:4px 0}.history-pages{display:flex;justify-content:space-between;align-items:center;padding-top:16px;font-size:13px}
@media(max-width:900px){.stock-filters{grid-template-columns:minmax(0,1fr) 1fr 1fr}.search-field{grid-column:1/-1}.unknown-filter{justify-content:flex-end}}@media(max-width:650px){.warehouse-heading{align-items:flex-start}.warehouse-heading .btn{font-size:12px;padding:9px}.warehouse-heading .page-lead{max-width:230px}.metrics-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:8px}.metric-card{padding:12px}.metric-card strong{font-size:26px}.warehouse-toolbar{flex-wrap:wrap;gap:0}.toolbar-actions{margin-left:auto}.pallet-grid{grid-template-columns:1fr}.stock-filters{gap:10px;padding:12px;grid-template-columns:repeat(2,minmax(0,1fr))}.unknown-filter{grid-column:1/-1;justify-content:flex-start}.warehouse-dialog{padding:16px}.results-heading h2 span{display:block;margin:5px 0 0}.active-filters button{min-height:44px}}
</style>
