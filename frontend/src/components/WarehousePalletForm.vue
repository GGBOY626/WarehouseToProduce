<script setup lang="ts">
import { computed, ref } from 'vue';
import { warehouseApi } from '../api/warehouse';
import { productsApi } from '../api';
import { errorMessage } from '../api/http';
import { createUuid } from '../utils/uuid';
import { areaLanes, laneLabels, nextPosition, rackStart } from '../utils/warehouse';
import SearchPicker from './SearchPicker.vue';
import type { Product } from '../types';
import { warehouseProductDefaults } from '../utils/warehouseProductDefaults';
import type { WarehouseArea, WarehousePallet, PalletInput, StockItem } from '../types/warehouse';
const props=defineProps<{areas:WarehouseArea[];pallets:WarehousePallet[];products:Product[];pallet?:WarehousePallet;append?:boolean;initialAreaId?:number;initialLane?:string}>();
const emit=defineEmits<{saved:[pallet:WarehousePallet,continueEntry:boolean];cancel:[]}>();
const activePallet=ref(props.pallet),pallet=computed(()=>activePallet.value);
const code=ref(props.pallet?.code || `TP-${createUuid().slice(0,8).toUpperCase()}`),areaId=ref(props.pallet?.areaId||props.initialAreaId||props.areas[0]?.id),lane=ref(props.pallet?.lane||props.initialLane||'LOWER');
const initialArea=props.areas.find(a=>a.id===areaId.value);
const position=ref(props.pallet?.position||nextPosition(props.pallets,areaId.value!,lane.value,initialArea?.kind==='RACK'?rackStart(initialArea.name):1,initialArea?.kind==='RACK'?18:9999)),notes=ref(props.pallet?.notes||'');
type DraftItem={key:number;productId:number|undefined;productName:string;materialCode:string;productionNumber:string;unit:string;rawMaterialBatch:string;batch:string;materialCodeOptions:string[];productionNumberOptions:string[];rawMaterialBatchOptions:string[];batchOptions:string[];quantity:number|null|'';cartons:number|null|'';unitsPerCarton:number|null|'';boxesPerCarton:number|null|'';unitsPerBox:number|null|'';finished:boolean|null;photoId?:number|null;photoUrl?:string|null};
let key=0;
function blankItem():DraftItem{return {key:key++,productId:undefined,productName:'',materialCode:'',productionNumber:'',unit:'',rawMaterialBatch:'',batch:'',materialCodeOptions:[],productionNumberOptions:[],rawMaterialBatchOptions:[],batchOptions:[],quantity:null,cartons:null,unitsPerCarton:'',boxesPerCarton:'',unitsPerBox:'',finished:null}}
function draftItem(item:StockItem):DraftItem{return {...blankItem(),...item,productId:item.productId??undefined,materialCode:item.materialCode||'',productionNumber:item.productionNumber||'',unit:item.unit||'',rawMaterialBatch:item.rawMaterialBatch||'',batch:item.batch||'',unitsPerCarton:item.unitsPerCarton??'',boxesPerCarton:item.boxesPerCarton??'',unitsPerBox:item.unitsPerBox??'',finished:item.finished??null}}
function hasDraftContent(item:DraftItem){return Boolean(item.productId||item.productName.trim()||item.materialCode||item.productionNumber||item.rawMaterialBatch||item.batch||item.photoId||item.quantity!==null&&item.quantity!==''||item.cartons!==null&&item.cartons!=='')}
const items=ref<DraftItem[]>(props.pallet&&!props.append?props.pallet.items.map(draftItem):[blankItem()]);
const busy=ref(false),message=ref('');
const area=computed(()=>props.areas.find(a=>a.id===areaId.value));
const lanes=computed(()=>areaLanes(area.value));
const customPosition=ref(false);
const positionStart=computed(()=>area.value?.kind==='RACK'?rackStart(area.value.name):1);
const rackPositions=computed(()=>Array.from({length:19-positionStart.value},(_,index)=>positionStart.value+index));
const occupiedPallet=computed(()=>props.pallets.find(p=>p.id!==activePallet.value?.id&&p.areaId===areaId.value&&p.lane===lane.value&&p.position===position.value));
const occupied=computed(()=>Boolean(occupiedPallet.value));
function changeArea(){lane.value=area.value?.kind==='RACK'?'LOWER':'LEFT_1';customPosition.value=false;suggestPosition()}
function suggestPosition(){customPosition.value=false;position.value=nextPosition(props.pallets,areaId.value!,lane.value,area.value?.kind==='RACK'?rackStart(area.value.name):1,area.value?.kind==='RACK'?18:9999)}
function setFinished(item:DraftItem,finished:boolean){
  item.finished=finished;
  item.productId=undefined;item.productName='';item.unit='';item.materialCode='';item.productionNumber='';item.rawMaterialBatch='';item.batch='';
  item.materialCodeOptions=[];item.productionNumberOptions=[];item.rawMaterialBatchOptions=[];item.batchOptions=[];
}
function appendToExisting(){
  const existing=occupiedPallet.value;if(!existing)return;
  const additions=items.value.filter(hasDraftContent);
  activePallet.value=existing;code.value=existing.code;notes.value=[existing.notes,notes.value].filter(Boolean).join('\n');
  items.value=[...existing.items.map(draftItem),...additions,...(additions.length?[]:[blankItem()])];message.value='';
}
async function searchProducts(query:string,item:DraftItem){return productsApi.search(query,true,item.finished?'PRODUCTION_TO_WAREHOUSE':'WAREHOUSE_TO_PRODUCTION')}
function changedProduct(item:DraftItem,selected?:Product){
  const p=selected||props.products.find(product=>product.id===item.productId);
  item.productName=p?.name||'';item.unit=p?.baseUnit||'';
  const defaults=warehouseProductDefaults(p,item.finished===true);
  item.materialCodeOptions=defaults.materialCodeOptions;item.productionNumberOptions=defaults.productionNumberOptions;item.rawMaterialBatchOptions=defaults.rawMaterialBatchOptions;item.batchOptions=defaults.batchOptions;
  item.materialCode=defaults.materialCode;item.productionNumber=defaults.productionNumber;item.rawMaterialBatch=defaults.rawMaterialBatch;item.batch=defaults.batch;
}
function cancel(){if(busy.value)return;emit('cancel')}
async function save(continueEntry=false){
  if(busy.value)return;message.value='';
  if(items.value.some(i=>i.finished===null)){message.value='请为每项物料选择是成品还是非成品。';return}
  if(!areaId.value||items.value.some(i=>!i.productName.trim())){message.value='请选择库区，并填写产品名称。';return}
  if(occupied.value){message.value='该位置已有托盘，请换一个位置。';return}
  const allItems=props.append&&props.pallet?[...props.pallet.items.map(draftItem),...items.value]:items.value;
  const data:PalletInput={code:code.value.trim(),areaId:areaId.value,lane:lane.value,position:position.value,notes:notes.value,version:activePallet.value?.version,items:allItems.map(i=>({productId:i.productId,productName:i.productName.trim(),materialCode:i.materialCode.trim(),productionNumber:i.productionNumber.trim(),unit:i.unit.trim(),rawMaterialBatch:i.rawMaterialBatch.trim(),photoId:i.photoId,batch:i.batch.trim(),quantity:i.quantity===''?null:i.quantity,cartons:i.cartons===''?null:i.cartons,unitsPerCarton:i.unitsPerCarton===''?null:i.unitsPerCarton,boxesPerCarton:i.boxesPerCarton===''?null:i.boxesPerCarton,unitsPerBox:i.unitsPerBox===''?null:i.unitsPerBox,finished:i.finished!}))};
  busy.value=true;
  try{
    const saved=activePallet.value?await warehouseApi.update(activePallet.value.id,data):await warehouseApi.create(data);
    emit('saved',saved,continueEntry&&!activePallet.value);
    if(continueEntry&&!activePallet.value){
      activePallet.value=undefined;code.value=`TP-${createUuid().slice(0,8).toUpperCase()}`;notes.value='';items.value=[blankItem()];message.value='托盘已保存，请录入下一托盘。';
      position.value=nextPosition([...props.pallets,saved],areaId.value!,lane.value,area.value?.kind==='RACK'?rackStart(area.value.name):1,area.value?.kind==='RACK'?18:9999);customPosition.value=false;
    }
  }catch(e){message.value=errorMessage(e)}finally{busy.value=false}
}
</script>
<template>
  <form class="pallet-form" @submit.prevent="save()">
    <header><div><span class="hint">{{pallet ? '库存与货位' : '现场录入'}}</span><h2>{{pallet ? (props.append?'继续追加货物':'编辑 / 移位托盘') : '录入托盘'}}</h2></div><button class="btn btn-secondary" type="button" :disabled="busy" @click="cancel">关闭</button></header>
    <p v-if="message" class="error-text" role="alert">{{message}}</p>
    <fieldset :disabled="busy"><legend class="sr-only">托盘资料</legend>
      <div v-if="!props.append" class="location-fields">
        <div class="field"><label for="pallet-area">库区</label><select id="pallet-area" v-model="areaId" class="select" required @change="changeArea"><option v-for="a in areas" :key="a.id" :value="a.id">{{a.rackRow?`${a.rackRow} 排旁 · `:''}}{{a.name}}{{a.kind==='RACK'?' 排':''}}</option></select></div>
        <div class="field"><label for="pallet-lane">{{area?.kind==='RACK'?'层位':'排位'}}</label><select id="pallet-lane" v-model="lane" class="select" required @change="suggestPosition"><option v-for="l in lanes" :key="l" :value="l">{{laneLabels[l]}}</option></select></div>
        <div class="field"><label for="pallet-position">位置序号</label><template v-if="area?.kind==='RACK'"><select v-if="!customPosition" id="pallet-position" v-model.number="position" class="select" required><option v-for="number in rackPositions" :key="number" :value="number">{{String(number).padStart(2,'0')}}</option></select><input v-else id="pallet-position" v-model.number="position" class="input" type="number" :min="positionStart" max="18" step="1" required><button class="text-button" type="button" @click="customPosition=!customPosition">{{customPosition?'从序号列表选择':'手动输入序号'}}</button></template><input v-else id="pallet-position" v-model.number="position" class="input" type="number" min="1" max="9999" step="1" required></div>
      </div>
      <p v-if="!props.append" class="location-preview" :class="{conflict:occupied}">{{area?.kind==='RACK'?`${area.name}-${String(position).padStart(2,'0')}`:`${area?.name} / ${position} 号`}} / {{laneLabels[lane]}} <b v-if="occupied">· 已有托盘</b></p>
      <div v-if="!props.append&&occupiedPallet" class="append-warning" role="alert"><span>该位置已有托盘。一个托盘可以登记多种产品，是否继续追加到现有托盘？</span><button class="btn btn-danger" type="button" @click="appendToExisting">继续追加</button></div>
      <p v-if="!props.append" class="hint">AA、BB、CC、EE 排从 05 编至 18；FF、HH、KK、LL、RR、SS 排从 01 编至 18。</p>
      <div class="section-head"><h3>托盘上的物料</h3><button class="btn btn-secondary" type="button" :disabled="items.length>=50" @click="items.push(blankItem())">＋ 添加物料</button></div>
      <p class="hint">产品名称可直接填写，也可搜索并选择产品管理中的已有产品；数量留空表示未知。</p>
      <section v-for="(item,index) in items" :key="item.key" class="stock-line">
        <div class="section-head"><b>物料 {{index+1}}</b><button v-if="items.length>1" type="button" class="btn btn-ghost" :aria-label="`移除物料 ${index+1}`" @click="items.splice(index,1)">移除</button></div>
        <div class="finish-choice"><span class="field-label required">是否成品</span><div><label><input v-model="item.finished" type="radio" :name="`finished-${item.key}`" :value="true" required @change="setFinished(item,true)">是，成品</label><label><input v-model="item.finished" type="radio" :name="`finished-${item.key}`" :value="false" required @change="setFinished(item,false)">不是成品</label></div></div>
        <template v-if="item.finished!==null">
          <SearchPicker v-model="item.productId" v-model:text-value="item.productName" free-text :max-length="200" empty-text="没有匹配的已有产品，可直接使用输入名称" label="产品名称" :placeholder="item.finished?'填写产品名称，或搜索车间 → 仓库产品':'填写产品名称，或搜索仓库 → 车间产品'" :load="query=>searchProducts(query,item)" :display="p=>p.name" @select="changedProduct(item,$event)">
            <template #default="{item:p}">{{p.name}} · {{(p.materialCodes?.length?p.materialCodes:[p.materialCode]).join(' / ')}}</template>
          </SearchPicker>
          <template v-if="item.finished">
            <div v-if="item.batchOptions.length>1" class="field"><label :for="`stock-batch-choice-${item.key}`">产品有多个编码，请选择 BATCH</label><select :id="`stock-batch-choice-${item.key}`" class="select" :value="item.batch" @change="item.batch=($event.target as HTMLSelectElement).value"><option value="">选择 BATCH…</option><option v-for="option in item.batchOptions" :key="option" :value="option">{{option}}</option></select></div>
            <div class="field"><label :for="`stock-batch-${item.key}`">BATCH（可选）</label><input :id="`stock-batch-${item.key}`" v-model="item.batch" class="input" maxlength="100" placeholder="填写 BATCH"></div>
            <div class="field"><label :for="`stock-sku-${item.key}`">SKU（可选）</label><input :id="`stock-sku-${item.key}`" v-model="item.materialCode" class="input" maxlength="100" placeholder="填写 SKU"></div>
          </template>
          <template v-else>
            <div v-if="item.materialCodeOptions.length>1" class="field"><label :for="`stock-code-choice-${item.key}`">产品有多个物料编码，请选择</label><select :id="`stock-code-choice-${item.key}`" class="select" :value="item.materialCode" @change="item.materialCode=($event.target as HTMLSelectElement).value"><option value="">选择物料编码…</option><option v-for="option in item.materialCodeOptions" :key="option" :value="option">{{option}}</option></select></div>
            <div class="field"><label :for="`stock-code-${item.key}`">物料编码（可选）</label><input :id="`stock-code-${item.key}`" v-model="item.materialCode" class="input" maxlength="100" placeholder="填写物料编码"></div>
            <div v-if="item.productionNumberOptions.length>1" class="field"><label :for="`stock-production-choice-${item.key}`">产品有多个生产编号，请选择</label><select :id="`stock-production-choice-${item.key}`" class="select" :value="item.productionNumber" @change="item.productionNumber=($event.target as HTMLSelectElement).value"><option value="">选择生产编号…</option><option v-for="option in item.productionNumberOptions" :key="option" :value="option">{{option}}</option></select></div>
            <div class="field"><label :for="`stock-production-number-${item.key}`">生产编号（可选）</label><input :id="`stock-production-number-${item.key}`" v-model="item.productionNumber" class="input" maxlength="100" placeholder="填写生产编号"></div>
            <div v-if="item.rawMaterialBatchOptions.length>1" class="field"><label :for="`stock-material-batch-choice-${item.key}`">产品有多个物料批次，请选择</label><select :id="`stock-material-batch-choice-${item.key}`" class="select" :value="item.rawMaterialBatch" @change="item.rawMaterialBatch=($event.target as HTMLSelectElement).value"><option value="">选择物料批次…</option><option v-for="option in item.rawMaterialBatchOptions" :key="option" :value="option">{{option}}</option></select></div>
            <div class="field"><label :for="`stock-material-batch-${item.key}`">物料批次（可选）</label><input :id="`stock-material-batch-${item.key}`" v-model="item.rawMaterialBatch" class="input" maxlength="100" placeholder="填写黄标五位数的号码"></div>
          </template>
          <div class="field"><label :for="`stock-quantity-${item.key}`">数量（可选）</label><input :id="`stock-quantity-${item.key}`" v-model.number="item.quantity" class="input" type="number" min="1" max="9007199254740991" step="1" placeholder="留空表示未知"></div>
        </template>
      </section>
      <div class="field"><label for="pallet-notes">备注</label><textarea id="pallet-notes" v-model="notes" class="textarea" maxlength="2000" placeholder="如：靠门一侧、待核对标签、零散尾箱"/></div>
    </fieldset>
    <footer><button class="btn btn-secondary" type="button" :disabled="busy" @click="cancel">取消</button><button v-if="!pallet" class="btn btn-secondary" type="button" :disabled="busy||occupied" @click="save(true)">{{busy?'正在处理…':'保存并录入下一托盘'}}</button><button class="btn btn-primary" :disabled="busy||occupied">{{busy?'正在处理…':pallet?(props.append?'保存追加':'保存修改'):'保存托盘'}}</button></footer>
  </form>
</template>
<style scoped>
.pallet-form{display:grid;gap:16px}.pallet-form header,.pallet-form footer{display:flex;justify-content:space-between;align-items:center;gap:12px}.pallet-form h2{margin:4px 0;font-size:21px}.pallet-form h3{font-size:16px;margin:0}.pallet-form fieldset{border:0;padding:0;margin:0;min-width:0;display:grid;gap:14px}.location-fields{display:grid;grid-template-columns:1.2fr 1fr 1fr;gap:10px}.location-preview{background:var(--soft-blue);border-left:4px solid var(--blue);padding:12px;margin:0;font-weight:700}.location-preview.conflict{background:var(--soft-red);color:var(--red)}.stock-line{border:1px solid var(--line);background:#f8fafb;border-radius:9px;padding:14px;display:grid;gap:12px}.stock-line .section-head{margin:0}.check-row{display:flex;align-items:center;gap:8px;font-size:13px;line-height:1.6;min-height:44px}.check-row input{width:18px;height:18px}.pallet-form footer{position:sticky;bottom:-20px;padding:14px 0;background:white;border-top:1px solid var(--line);justify-content:flex-end}.pallet-form .hint{margin:0}@media(max-width:440px){.location-fields{grid-template-columns:1fr 1fr}.location-fields>.field:first-child{grid-column:1/-1}}
</style>
<style scoped>
.item-photo{display:grid;gap:8px;font-size:13px}.item-photo input{max-width:100%;min-width:0}.item-photo img{display:block;width:120px;height:100px;object-fit:cover;border:1px solid var(--line);border-radius:8px}.item-photo .btn{justify-self:start}.nearby-aisles{display:flex;flex-wrap:wrap;gap:8px;align-items:center}
.append-warning{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px;border:1px solid var(--red);border-radius:6px;color:var(--red);background:var(--soft-red);font-size:13px}.append-warning .btn{flex-shrink:0}.finish-choice{display:grid;gap:7px}.finish-choice>div{display:flex;flex-wrap:wrap;gap:10px}.finish-choice label{display:flex;align-items:center;gap:7px;min-height:44px;padding:7px 12px;border:1px solid var(--line);border-radius:6px;cursor:pointer}.finish-choice input{width:17px;height:17px;accent-color:var(--blue)}@media(max-width:440px){.append-warning{align-items:flex-start;flex-direction:column}}
</style>
