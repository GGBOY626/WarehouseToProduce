<script setup lang="ts">
import { computed, ref, onBeforeUnmount } from 'vue';
import { warehouseApi } from '../api/warehouse';
import { errorMessage } from '../api/http';
import { createUuid } from '../utils/uuid';
import { preparePhoto } from '../utils/photo';
import { areaLanes, laneLabels, nextPosition } from '../utils/warehouse';
import type { Product } from '../types';
import type { WarehouseArea, WarehousePallet, PalletInput } from '../types/warehouse';
const props=defineProps<{areas:WarehouseArea[];pallets:WarehousePallet[];products:Product[];pallet?:WarehousePallet;initialAreaId?:number;initialLane?:string;continueEntry?:boolean}>();
const emit=defineEmits<{saved:[pallet:WarehousePallet,another:boolean];cancel:[]}>();
const code=ref(props.pallet?.code || `TP-${createUuid().slice(0,8).toUpperCase()}`),areaId=ref(props.pallet?.areaId||props.initialAreaId||props.areas[0]?.id),lane=ref(props.pallet?.lane||props.initialLane||'LOWER');
const position=ref(props.pallet?.position||nextPosition(props.pallets,areaId.value!,lane.value)),notes=ref(props.pallet?.notes||'');
type DraftItem={key:number;productId:number|undefined;mode:'manual'|'catalog';productName:string;materialCode:string;unit:string;rawMaterialBatch:string;batch:string;quantity:number|null|'';cartons:number|null|'';photoId?:number|null;photoUrl?:string|null};
let key=0;
function blankItem():DraftItem{return {key:key++,productId:undefined,mode:'manual',productName:'',materialCode:'',unit:'',rawMaterialBatch:'',batch:'',quantity:null,cartons:null}}
const items=ref<DraftItem[]>(props.pallet?.items.map(i=>({...blankItem(),...i,productId:i.productId??undefined,mode:i.productId?'catalog':'manual',materialCode:i.materialCode||'',unit:i.unit||'',rawMaterialBatch:i.rawMaterialBatch||'',batch:i.batch||''}))||[blankItem()]);
const busy=ref(false),message=ref(''),another=ref(props.continueEntry||false),productQuery=ref('');
const area=computed(()=>props.areas.find(a=>a.id===areaId.value));
const nearbyAisles=computed(()=>props.areas.filter(a=>a.kind==='AISLE'&&a.rackRow===area.value?.name));
const lanes=computed(()=>areaLanes(area.value));
const occupied=computed(()=>props.pallets.some(p=>p.id!==props.pallet?.id&&p.areaId===areaId.value&&p.lane===lane.value&&p.position===position.value));
function changeArea(){lane.value=area.value?.kind==='RACK'?'LOWER':'LEFT_1';suggestPosition()}
function suggestPosition(){position.value=nextPosition(props.pallets,areaId.value!,lane.value)}
function availableProducts(item:DraftItem){const q=productQuery.value.trim().toLowerCase();return props.products.filter(p=>p.id===item.productId||(p.active&&(!q||`${p.name} ${p.materialCode}`.toLowerCase().includes(q))))}
function product(id?:number){return props.products.find(p=>p.id===id)}
function changedProduct(item:DraftItem){const p=product(item.productId);item.productName=p?.name||'';item.materialCode=p?.materialCode||'';item.unit=p?.baseUnit||''}
function changeMode(item:DraftItem){item.productId=undefined}
const stagedPhotos=new Set<number>();let disposed=false;
function discardStaged(){for(const id of stagedPhotos)void warehouseApi.discardPhoto(id).catch(()=>{});stagedPhotos.clear()}
onBeforeUnmount(()=>{disposed=true;discardStaged()});
function cancel(){if(busy.value)return;discardStaged();emit('cancel')}
async function upload(item:DraftItem,event:Event){
  const input=event.target as HTMLInputElement,file=input.files?.[0];if(!file)return;input.value='';message.value='';
  if(file.size>12*1024*1024){message.value='照片不能超过 12MB，请选择较小的照片。';return}
  busy.value=true;
  try{const photo=await warehouseApi.uploadPhoto(await preparePhoto(file));if(disposed){await warehouseApi.discardPhoto(photo.id);return}stagedPhotos.add(photo.id);item.photoId=photo.id;item.photoUrl=photo.url}catch(e){message.value=errorMessage(e)}finally{busy.value=false}
}
async function save(){
  if(busy.value)return;message.value='';
  if(!areaId.value||items.value.some(i=>i.mode==='catalog'?!i.productId:!i.productName.trim())){message.value='请选择库区，并为每行物料填写产品名称或选择已有产品。';return}
  if(occupied.value){message.value='该位置已有托盘，请换一个位置。';return}
  const data:PalletInput={code:code.value.trim(),areaId:areaId.value,lane:lane.value,position:position.value,notes:notes.value,version:props.pallet?.version,items:items.value.map(i=>({productId:i.mode==='catalog'?i.productId:undefined,productName:i.productName.trim(),materialCode:i.materialCode.trim(),unit:i.unit.trim(),rawMaterialBatch:i.rawMaterialBatch.trim(),photoId:i.photoId,batch:i.batch.trim(),quantity:i.quantity===''?null:i.quantity,cartons:i.cartons===''?null:i.cartons}))};
  busy.value=true;
  try{const saved=props.pallet?await warehouseApi.update(props.pallet.id,data):await warehouseApi.create(data);discardStaged();emit('saved',saved,another.value)}catch(e){message.value=errorMessage(e)}finally{busy.value=false}
}
</script>
<template>
  <form class="pallet-form" @submit.prevent="save">
    <header><div><span class="hint">{{pallet ? '库存与货位' : '现场录入'}}</span><h2>{{pallet ? '编辑 / 移位托盘' : '录入托盘'}}</h2></div><button class="btn btn-secondary" type="button" :disabled="busy" @click="cancel">关闭</button></header>
    <p v-if="message" class="error-text" role="alert">{{message}}</p>
    <fieldset :disabled="busy"><legend class="sr-only">托盘资料</legend>
      <div class="field"><label for="pallet-code">托盘编号</label><input id="pallet-code" v-model="code" class="input mono" required maxlength="60"><span class="hint">可用自动编号，也可填写托盘现有标签。</span></div>
      <div class="location-fields">
        <div class="field"><label for="pallet-area">库区</label><select id="pallet-area" v-model="areaId" class="select" required @change="changeArea"><option v-for="a in areas" :key="a.id" :value="a.id">{{a.rackRow?`${a.rackRow} 排旁 · `:''}}{{a.name}}{{a.kind==='RACK'?' 排':''}}</option></select></div>
        <div class="field"><label for="pallet-lane">{{area?.kind==='RACK'?'层位':'排位'}}</label><select id="pallet-lane" v-model="lane" class="select" required @change="suggestPosition"><option v-for="l in lanes" :key="l" :value="l">{{laneLabels[l]}}</option></select></div>
        <div class="field"><label for="pallet-position">位置序号</label><input id="pallet-position" v-model.number="position" class="input" type="number" min="1" max="9999" step="1" required></div>
      </div>
      <div v-if="nearbyAisles.length" class="nearby-aisles"><span class="hint">货在 {{area?.name}} 排旁边的过道？</span><button v-for="a in nearbyAisles" :key="a.id" class="btn btn-secondary" type="button" @click="areaId=a.id;changeArea()">选择 {{a.rackRow}} 排旁过道 · {{a.lanes}} 排</button></div>
      <p class="location-preview" :class="{conflict:occupied}">{{area?.name}}{{area?.kind==='RACK'?' 排':''}} / {{laneLabels[lane]}} / {{position}} 号 <b v-if="occupied">· 已有托盘</b></p>
      <p class="hint">位置按现场编号填写。同一过道请始终朝同一方向分左右；左一、右一为常规排，左二、右二为临时增加排。</p>
      <div class="section-head"><h3>托盘上的物料</h3><button class="btn btn-secondary" type="button" :disabled="items.length>=50" @click="items.push(blankItem())">＋ 添加物料</button></div>
      <p class="hint">物料只需产品名称即可保存；照片、原料批号、批号和数量均可稍后补充。</p>
      <div v-if="items.some(i=>i.mode==='catalog')" class="field"><label for="stock-product-search">筛选已有产品</label><input id="stock-product-search" v-model="productQuery" class="input" type="search" placeholder="输入产品名称或物料编码"></div>
      <section v-for="(item,index) in items" :key="item.key" class="stock-line">
        <div class="section-head"><b>物料 {{index+1}}</b><button v-if="items.length>1" type="button" class="btn btn-ghost" :aria-label="`移除物料 ${index+1}`" @click="items.splice(index,1)">移除</button></div>
        <div class="entry-mode" role="group" :aria-label="`物料 ${index+1} 录入方式`"><button type="button" :aria-pressed="item.mode==='manual'" @click="item.mode='manual';changeMode(item)">手动输入名称</button><button type="button" :aria-pressed="item.mode==='catalog'" @click="item.mode='catalog'">选择已有产品</button></div>
        <div v-if="item.mode==='manual'" class="field"><label :for="`stock-name-${item.key}`" class="required">产品名称</label><input :id="`stock-name-${item.key}`" v-model="item.productName" class="input" required maxlength="200" placeholder="直接填写，例如：备用外盒"></div>
        <div v-else class="field"><label :for="`stock-product-${item.key}`" class="required">已有产品</label><select :id="`stock-product-${item.key}`" v-model="item.productId" class="select" required @change="changedProduct(item)"><option :value="undefined" disabled>选择产品</option><option v-for="p in availableProducts(item)" :key="p.id" :value="p.id">{{p.name}} · {{p.materialCode}}{{p.active?'':'（已停用）'}}</option></select></div>
        <div class="item-photo"><label :for="`stock-photo-${item.key}`">产品照片（可选）</label><a v-if="item.photoUrl" :href="item.photoUrl" target="_blank" rel="noopener"><img :src="item.photoUrl" :alt="item.productName||'产品照片'"></a><input :id="`stock-photo-${item.key}`" type="file" accept="image/jpeg,image/png" @change="upload(item,$event)"><span class="hint">支持 JPG、PNG，可拍照或选取相册图片。</span><button v-if="item.photoId" class="btn btn-ghost" type="button" @click="item.photoId=null;item.photoUrl=null">移除照片</button></div>
        <div class="field"><label :for="`stock-raw-batch-${item.key}`">原料批号（可选）</label><input :id="`stock-raw-batch-${item.key}`" v-model="item.rawMaterialBatch" class="input" maxlength="100" placeholder="填写原料批号"></div>
        <div class="field"><label :for="`stock-batch-${item.key}`">批号</label><input :id="`stock-batch-${item.key}`" v-model="item.batch" class="input" maxlength="100" :list="`batches-${item.key}`" placeholder="填写实际批号；暂不清楚可留空"><datalist :id="`batches-${item.key}`"><option v-for="batch in product(item.productId)?.materialBatches" :key="batch" :value="batch"/></datalist></div>
        <div v-if="item.mode==='manual'" class="grid-2"><div class="field"><label :for="`stock-code-${item.key}`">物料编码（可选）</label><input :id="`stock-code-${item.key}`" v-model="item.materialCode" class="input" maxlength="100"></div><div class="field"><label :for="`stock-unit-${item.key}`">单位（可选）</label><input :id="`stock-unit-${item.key}`" v-model="item.unit" class="input" maxlength="20" placeholder="例如：个、条、盒"></div></div>
        <div class="grid-2"><div class="field"><label :for="`stock-quantity-${item.key}`">数量{{item.unit ? `（${item.unit}）` : ''}}</label><input :id="`stock-quantity-${item.key}`" v-model.number="item.quantity" class="input" type="number" min="1" max="9007199254740991" step="1" placeholder="留空 = 待盘点"></div><div class="field"><label :for="`stock-cartons-${item.key}`">箱数（可选）</label><input :id="`stock-cartons-${item.key}`" v-model.number="item.cartons" class="input" type="number" min="0" max="2147483647" step="1" placeholder="填写实际箱数"></div></div>
      </section>
      <p class="hint">数量是物料总数，箱数仅作辅助记录，不自动换算。手动填写的产品仅记录在当前托盘中。</p>
      <div class="field"><label for="pallet-notes">备注</label><textarea id="pallet-notes" v-model="notes" class="textarea" maxlength="2000" placeholder="如：靠门一侧、待核对标签、零散尾箱"/></div>
      <label v-if="!pallet" class="check-row"><input v-model="another" type="checkbox">保存后继续录下一托盘（保留库区与层位）</label>
    </fieldset>
    <footer><button class="btn btn-secondary" type="button" :disabled="busy" @click="cancel">取消</button><button class="btn btn-primary" :disabled="busy||occupied">{{busy?'正在处理…':pallet?'保存修改':'保存托盘'}}</button></footer>
  </form>
</template>
<style scoped>
.pallet-form{display:grid;gap:16px}.pallet-form header,.pallet-form footer{display:flex;justify-content:space-between;align-items:center;gap:12px}.pallet-form h2{margin:4px 0;font-size:21px}.pallet-form h3{font-size:16px;margin:0}.pallet-form fieldset{border:0;padding:0;margin:0;min-width:0;display:grid;gap:14px}.location-fields{display:grid;grid-template-columns:1.2fr 1fr 1fr;gap:10px}.location-preview{background:var(--soft-blue);border-left:4px solid var(--blue);padding:12px;margin:0;font-weight:700}.location-preview.conflict{background:var(--soft-red);color:var(--red)}.stock-line{border:1px solid var(--line);background:#f8fafb;border-radius:9px;padding:14px;display:grid;gap:12px}.stock-line .section-head{margin:0}.check-row{display:flex;align-items:center;gap:8px;font-size:13px;line-height:1.6;min-height:44px}.check-row input{width:18px;height:18px}.pallet-form footer{position:sticky;bottom:-20px;padding:14px 0;background:white;border-top:1px solid var(--line);justify-content:flex-end}.pallet-form .hint{margin:0}@media(max-width:440px){.location-fields{grid-template-columns:1fr 1fr}.location-fields>.field:first-child{grid-column:1/-1}}
</style>
<style scoped>
.entry-mode{display:flex;gap:5px;background:#e9eef0;padding:4px;border-radius:8px}.entry-mode button{flex:1;min-height:40px;border:0;border-radius:5px;background:transparent;font-size:13px;cursor:pointer}.entry-mode button[aria-pressed=true]{background:white;color:var(--blue);font-weight:700}.item-photo{display:grid;gap:8px;font-size:13px}.item-photo input{max-width:100%;min-width:0}.item-photo img{display:block;width:120px;height:100px;object-fit:cover;border:1px solid var(--line);border-radius:8px}.item-photo .btn{justify-self:start}.nearby-aisles{display:flex;flex-wrap:wrap;gap:8px;align-items:center}
</style>
