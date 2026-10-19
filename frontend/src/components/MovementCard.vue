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
  <div class="movement-card card">
  <RouterLink :to="`/movements/${movement.id}`" class="record-link">
    <div class="movement-items">
      <MaterialSummary compact v-for="item in movement.items" :key="item.id" v-bind="item" :show-material-code="movement.direction === 'PRODUCTION_TO_WAREHOUSE' && !movement.returnMovement" :show-material-batch="true" />
    </div>
    <div class="record-footer">
      <time :datetime="movement.movementTime">{{ showDate ? showDateTime(movement.movementTime) : showTime(movement.movementTime) }}</time>
      <span :class="['movement-direction', {return:movement.direction==='PRODUCTION_TO_WAREHOUSE'}]">{{ directionLabel(movement.direction) }}<span v-if="movement.returnMovement"> · 退回</span></span>
      <span v-if="movement.finished" class="status ok">成品</span>
      <span class="photo-tag">照片 {{ movement.photoCount }}</span>
      <span v-if="movement.status==='VOID'" class="status neutral">已作废</span>
      <span v-if="movement.hasIssues" class="status warn">有异常</span>
    </div>
  </RouterLink>
  <div class="task-link-row"><RouterLink v-if="taskId" :to="`/production-tasks/${taskId}`" class="status ok">已关联 · {{taskName}}</RouterLink><button v-else-if="authenticated && movement.status==='ACTIVE'" class="btn btn-secondary" @click="openLink">关联任务</button></div>
  <dialog ref="dialog" class="link-dialog" aria-label="关联生产任务" @cancel="busy && $event.preventDefault()">
    <form @submit.prevent="saveLink"><h2>关联生产任务</h2><p>{{movement.recordNo}}</p><p v-if="message" role="alert">{{message}}</p><p v-if="busy">正在处理…</p><template v-else><label :for="`link-task-${movement.id}`">选择任务</label><select :id="`link-task-${movement.id}`" v-model="selected" required><option :value="undefined" disabled>请选择生产任务</option><option v-for="task in options" :key="task.id" :value="task.id">{{task.name}} · {{task.batchNo}}</option></select><p v-if="!options.length">没有匹配此记录物料的进行中任务。</p></template><div class="dialog-actions"><button type="button" class="btn btn-secondary" :disabled="busy" @click="dialog?.close()">取消</button><button class="btn btn-primary" :disabled="busy || !selected">确认关联</button></div></form>
  </dialog>
  </div>
</template>
<style scoped>
.movement-card{display:block;padding:12px 14px}.movement-items{display:grid;gap:10px}.movement-items>:not(:first-child){border-top:1px solid var(--line);padding-top:10px}
.record-footer{display:flex;flex-wrap:wrap;gap:5px 12px;align-items:center;margin-top:9px;font-size:12px;color:var(--muted)}
.movement-direction{color:var(--blue)}.movement-direction.return{color:var(--green)}.photo-tag{padding:2px 6px;background:#edf0f2;border-radius:5px;font-size:11px}
.record-footer .status{min-height:20px;font-size:11px;padding:2px 6px}
.record-link{display:block}.task-link-row{display:flex;justify-content:flex-end;margin-top:8px}.task-link-row:empty{display:none}.task-link-row .btn{min-height:36px;font-size:12px}.link-dialog{width:min(460px,calc(100vw - 32px));border:1px solid var(--line);border-radius:12px;padding:24px}.link-dialog::backdrop{background:#0006}.link-dialog select{width:100%;margin:10px 0}.dialog-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:20px}
</style>
