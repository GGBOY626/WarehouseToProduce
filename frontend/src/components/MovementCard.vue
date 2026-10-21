<script setup lang="ts">
import { ref, computed, nextTick } from 'vue';
import { movementsApi, productionTasksApi } from '../api';
import { errorMessage } from '../api/http';
import { useAuth } from '../auth';
import type { ProductionTaskOption } from '../types';
import type { MovementSummary } from '../types';
import { directionLabel } from '../utils/labels';
import { showDateTime, showTime } from '../utils/time';
import MaterialSummary from './MaterialSummary.vue';
const props=withDefaults(defineProps<{ movement: MovementSummary; showDate?: boolean }>(), { showDate: false });
const { authenticated }=useAuth();
const dialog=ref<HTMLDialogElement>(), options=ref<ProductionTaskOption[]>([]), selected=ref<number>(), busy=ref(false), message=ref('');
const linked=ref<{id:number;name:string}>();
const taskId=computed(()=>props.movement.productionTaskId || linked.value?.id);
const taskName=computed(()=>props.movement.productionTaskName || linked.value?.name);
async function openLink(){
  message.value='';selected.value=undefined;options.value=[];busy.value=true;
  await nextTick();dialog.value?.showModal();
  try{const detail=await movementsApi.detail(props.movement.id);options.value=await productionTasksApi.linkable(detail.items.map(i=>({productId:i.productId,materialBatch:i.materialBatch})),detail.direction,detail.returnMovement)}catch(e){message.value=errorMessage(e)}finally{busy.value=false}
}
async function saveLink(){
  if(!selected.value||busy.value)return;busy.value=true;message.value='';
  try{const detail=await movementsApi.linkTask(props.movement.id,selected.value);linked.value={id:detail.productionTaskId!,name:detail.productionTaskName!};dialog.value?.close()}catch(e){message.value=errorMessage(e)}finally{busy.value=false}
}
</script>
<template>
  <article class="movement-card card" :class="{ inbound: movement.direction === 'PRODUCTION_TO_WAREHOUSE' }">
  <RouterLink :to="`/movements/${movement.id}`" class="record-link" :aria-label="`${directionLabel(movement.direction)}，${movement.items.map(i => i.productName).join('、')}，查看流转详情`">
    <div class="record-heading">
      <span class="movement-direction"><span class="direction-icon" aria-hidden="true">{{ movement.direction === 'WAREHOUSE_TO_PRODUCTION' ? '↗' : '↙' }}</span>{{ directionLabel(movement.direction) }}<span class="direction-kind">{{ movement.returnMovement ? '退回' : movement.direction === 'WAREHOUSE_TO_PRODUCTION' ? '出库' : '入库' }}</span></span>
      <time :datetime="movement.movementTime">{{ showDate ? showDateTime(movement.movementTime) : showTime(movement.movementTime) }}</time>
    </div>
    <div class="movement-items">
      <MaterialSummary compact v-for="item in movement.items" :key="item.id" v-bind="item" :show-material-code="movement.direction === 'PRODUCTION_TO_WAREHOUSE' && !movement.returnMovement" :show-material-batch="true" />
    </div>
  </RouterLink>
    <footer class="record-footer">
      <div class="record-tags">
      <span class="photo-tag"><svg aria-hidden="true" viewBox="0 0 20 20" width="14" height="14"><path d="M3 5h3l1-2h6l1 2h3v12H3Z"/><circle cx="10" cy="10" r="3"/></svg>照片 {{ movement.photoCount }}</span>
      <span v-if="movement.finished" class="status ok">成品</span>
      <span v-if="movement.status==='VOID'" class="status neutral">已作废</span>
      <span v-if="movement.hasIssues" class="status warn">有异常</span>
      </div>
      <div class="task-link-row"><RouterLink v-if="taskId" :to="`/production-tasks/${taskId}`" class="linked-task">已关联 · {{taskName}}</RouterLink><button v-else-if="authenticated && movement.status==='ACTIVE'" type="button" class="link-task-button" @click="openLink">＋ 关联任务</button><span v-else class="unlinked-task">未关联任务</span></div>
    </footer>
  <dialog ref="dialog" class="link-dialog" aria-label="关联生产任务" @cancel="busy && $event.preventDefault()">
    <form @submit.prevent="saveLink"><h2>关联生产任务</h2><p>{{movement.recordNo}}</p><p v-if="message" role="alert">{{message}}</p><p v-if="busy">正在处理…</p><template v-else><label :for="`link-task-${movement.id}`">选择任务</label><select :id="`link-task-${movement.id}`" v-model="selected" required><option :value="undefined" disabled>请选择生产任务</option><option v-for="task in options" :key="task.id" :value="task.id">{{task.name}}</option></select><p v-if="!options.length">没有匹配此记录物料的进行中任务。</p></template><div class="dialog-actions"><button type="button" class="btn btn-secondary" :disabled="busy" @click="dialog?.close()">取消</button><button class="btn btn-primary" :disabled="busy || !selected">确认关联</button></div></form>
  </dialog>
  </article>
</template>
<style scoped>
.movement-card{--flow-color:var(--blue);--flow-soft:var(--soft-blue);position:relative;isolation:isolate;display:grid;gap:8px;padding:12px 16px 10px 20px;min-width:0;border-left:3px solid var(--flow-color);box-shadow:0 2px 5px rgba(20,39,50,.035)}
.movement-card.inbound{--flow-color:var(--green);--flow-soft:var(--soft-green)}
.movement-card:hover{border-color:var(--flow-color);box-shadow:0 3px 10px rgba(20,39,50,.07)}
.movement-card:active{background:var(--flow-soft)}
.record-link{display:grid;gap:9px;min-width:0}
.record-link::after{content:"";position:absolute;inset:0;z-index:1;border-radius:var(--radius)}
.record-link:focus-visible{outline:none}.record-link:focus-visible::after{outline:3px solid var(--flow-color);outline-offset:3px}
.record-heading{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:5px 12px;font-size:12px}
.record-heading time{color:var(--muted);font-variant-numeric:tabular-nums}
.movement-direction{display:flex;align-items:center;flex-wrap:wrap;gap:6px;color:var(--flow-color);font-weight:650}
.direction-icon{display:inline-grid;place-items:center;width:22px;height:22px;background:var(--flow-soft);border-radius:5px;font-size:16px}
.direction-kind{font-size:11px;font-weight:500;padding-left:6px;border-left:1px solid var(--line)}
.movement-items{display:grid;gap:8px;min-width:0}.movement-items>:not(:first-child){border-top:1px solid var(--line);padding-top:8px}
.movement-items :deep(.compact .material-name){font-size:16px;line-height:1.4}
.movement-items :deep(.material-identifiers b),.movement-items :deep(.quantity-total b){color:var(--flow-color)}
.movement-items :deep(.compact .material-quantities){gap:20px}
.movement-items :deep(.compact .material-quantities div){display:grid;gap:1px}
.movement-items :deep(.compact .material-quantities div>small){display:block;font-size:10px}
.movement-items :deep(.compact .material-quantities b){font-size:17px}
.record-footer{display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:4px 12px;font-size:12px;color:var(--muted)}
.record-tags{display:flex;align-items:center;flex-wrap:wrap;gap:6px}
.photo-tag{display:inline-flex;align-items:center;gap:4px;font-size:11px}.photo-tag svg{fill:none;stroke:currentColor;stroke-width:1.3}
.record-footer .status{min-height:20px;font-size:11px;padding:2px 6px}
.task-link-row{min-width:0;max-width:100%;overflow-wrap:anywhere}.linked-task,.link-task-button{position:relative;z-index:2;display:inline-block;padding:4px 7px;border:1px solid transparent;border-radius:5px;background:var(--flow-soft);color:var(--flow-color);font-size:11px;cursor:pointer}
.linked-task:hover,.link-task-button:hover{border-color:var(--flow-color)}
.linked-task:focus-visible,.link-task-button:focus-visible{outline:2px solid var(--flow-color);outline-offset:2px}
.unlinked-task{font-size:11px}
.link-dialog{width:min(460px,calc(100vw - 32px));border:1px solid var(--line);border-radius:12px;padding:24px}.link-dialog::backdrop{background:#0006}.link-dialog select{width:100%;margin:10px 0}.dialog-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:20px}
@media(max-width:560px){.movement-card{padding:12px 12px 10px 14px}.record-heading{font-size:11px}.movement-items :deep(.compact .material-name){font-size:15px}.linked-task,.link-task-button{min-height:36px;display:inline-flex;align-items:center}.movement-items :deep(.compact .material-quantities b){font-size:15px}}
</style>
