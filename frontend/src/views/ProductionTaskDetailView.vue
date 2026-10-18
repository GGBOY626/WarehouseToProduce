<script setup lang="ts">
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { productionTasksApi } from "../api";
import { errorMessage } from "../api/http";
import { useAuth } from "../auth";
import type { ProductionTask, ProductionTaskStatus } from "../types";
import ProductionTaskForm from "../components/ProductionTaskForm.vue";
import ProductionTaskOutput from "../components/ProductionTaskOutput.vue";
import ProductionTaskTargets from "../components/ProductionTaskTargets.vue";
import TaskMaterialHistory from "../components/TaskMaterialHistory.vue";
import { showDateTime } from "../utils/time";
import AiAssistant from '../components/AiAssistant.vue';
const id=Number(useRoute().params.id), router=useRouter(), task=ref<ProductionTask>(), message=ref("");
const { authenticated }=useAuth();
const editing=ref(false), changingStatus=ref(false), deleting=ref(false);
const dialog=ref<HTMLDialogElement>(), selected=ref<{productId:number;productName:string;finished:boolean}>();
const labels:Record<ProductionTaskStatus,string>={PREPARING:'进行中',IN_PROGRESS:'进行中',COMPLETED:'已完成',CANCELLED:'已取消'};
const productionPercent=computed(()=>task.value?.output?.quantityBasis ? task.value.output.productionPercent : null);
const movementDirection=ref('WAREHOUSE_TO_PRODUCTION');
const movementGroups=computed(()=>[
  { direction:'WAREHOUSE_TO_PRODUCTION', title:'仓库 → 车间', subtitle:'物料送入', rows:task.value?.movements.filter(m=>m.direction==='WAREHOUSE_TO_PRODUCTION') || [] },
  { direction:'PRODUCTION_TO_WAREHOUSE', title:'车间 → 仓库', subtitle:'成品回仓 / 退料', rows:task.value?.movements.filter(m=>m.direction==='PRODUCTION_TO_WAREHOUSE') || [] }
]);
const visibleMovementGroups=computed(()=>movementGroups.value.filter(group=>group.direction===movementDirection.value));
function saved(value:ProductionTask){task.value=value;editing.value=false}
async function load(){try{task.value=await productionTasksApi.detail(id)}catch(e){message.value=errorMessage(e)}}
async function setStatus(status:ProductionTaskStatus){if(changingStatus.value)return;changingStatus.value=true;message.value="";try{task.value=await productionTasksApi.status(id,status)}catch(e){message.value=errorMessage(e)}finally{changingStatus.value=false}}
async function deleteTask(){
  if(deleting.value || !window.confirm('确定删除这个生产任务？删除后无法恢复。关联流转单及照片会保留，只解除与任务的关联。'))return;
  deleting.value=true;message.value="";
  try{await productionTasksApi.delete(id);await router.replace('/production-tasks')}catch(e){message.value=errorMessage(e)}finally{deleting.value=false}
}
async function showHistory(product:{productId:number;productName:string},finished=false){selected.value={...product,finished};await nextTick();dialog.value?.showModal()}
let refreshTimer: ReturnType<typeof setInterval>;
function refreshVisible(){if(document.visibilityState==='visible'&&!editing.value&&!deleting.value&&!changingStatus.value&&!dialog.value?.open)void load()}
onMounted(()=>{void load();refreshTimer=setInterval(refreshVisible,15000);window.addEventListener('focus',refreshVisible)});
onBeforeUnmount(()=>{clearInterval(refreshTimer);window.removeEventListener('focus',refreshVisible)});
</script>
<template>
  <div class="page">
    <RouterLink to="/production-tasks" class="back-link">← 返回生产任务</RouterLink>
    <p v-if="message" class="form-alert" role="alert">{{ message }}</p>
    <div v-if="!task" class="card empty">正在读取生产任务…</div>
    <template v-else>
      <div class="task-head"><span class="status" :class="task.status==='COMPLETED'?'ok':'neutral'">{{ labels[task.status] }}</span><h1 class="page-title">{{ task.name }}</h1><p class="page-lead">{{ task.batchNo }} · 计划 {{ task.plannedDate }}</p></div>
      <AiAssistant :scope="{taskId:id}"/>
      <div class="progress-pair">
        <section class="progress-card material-progress" aria-labelledby="material-progress-title"><h2 id="material-progress-title">备料进度</h2><strong>{{ task.progressPercent }}<small>%</small></strong><div class="progress-track" role="progressbar" aria-label="备料进度" :aria-valuenow="task.progressPercent" :aria-valuemin="0" :aria-valuemax="100"><i :style="{width:`${task.progressPercent}%`}" /></div><p>各物料净发料 / 计划需求</p></section>
        <section class="progress-card production-progress" aria-labelledby="production-progress-title"><h2 id="production-progress-title">生产完成度</h2><strong>{{ productionPercent == null ? '—' : productionPercent }}<small v-if="productionPercent != null">%</small></strong><div class="progress-track" role="progressbar" aria-label="生产完成度" :aria-valuenow="productionPercent ?? undefined" :aria-valuetext="productionPercent == null ? '暂无可计算数据' : undefined" :aria-valuemin="0" :aria-valuemax="100"><i :style="{width:`${productionPercent || 0}%`}" /></div><p>{{ productionPercent == null ? '待净发料与包装数据齐全后计算' : '成品折算数量 / 主料净发料' }}</p></section>
      </div>
      <ProductionTaskForm v-if="editing" :task="task" @saved="saved" @cancel="editing=false" />
      <div v-else class="task-sections">
      <section class="card card-pad">
        <h2 class="section-title output-heading">成品生产情况</h2>
        <ProductionTaskOutput v-if="task.output" :output="task.output" interactive hide-progress class="output-panel" @select="showHistory(task.output!,true)" />
        <p v-else class="hint">此任务尚未设置成品及包装，编辑任务后即可关联成品回仓。</p>
      </section>
      <section class="section">
        <h2 class="section-title materials-heading">备料明细</h2>
        <ProductionTaskTargets :targets="task.targets" :completed="task.status==='COMPLETED'" interactive cards @select="showHistory($event)" />
        <details class="calculation-note"><summary>进度计算说明</summary><p>备料进度为各物料备料比例的平均值，不同批次合计，净发料 = 已发料 − 已退料。生产完成度 = 回仓折算数量 ÷ 主料实际净发料，仅统计关联的有效记录；任务由人工标记完成。包装物料预计耗用按计划配比折算，实际损耗需在生产结束后核对。</p></details>
      </section>
      </div>
      <p v-if="task.remarks" class="remark">{{ task.remarks }}</p>
      <section class="section"><div class="section-head"><h2 class="section-title">关联流转记录</h2><span class="hint">{{ task.movements.length }} 单</span></div>
        <div class="direction-switch" role="group" aria-label="流转方向">
          <button v-for="group in movementGroups" :key="group.direction" type="button" :aria-pressed="movementDirection===group.direction" aria-controls="task-movement-records" @click="movementDirection=group.direction">{{ group.title }} <small>{{ group.rows.length }}</small></button>
        </div>
        <div id="task-movement-records" class="movement-groups"><section v-for="group in visibleMovementGroups" :key="group.direction" class="movement-group"><div class="group-heading"><h3>{{ group.title }}</h3><span>{{ group.subtitle }} · {{ group.rows.length }} 单</span></div>
          <p v-if="!group.rows.length" class="group-empty">暂无此方向的流转记录</p>
          <RouterLink v-for="m in group.rows" :key="m.id" :to="`/movements/${m.id}`" class="movement-row">
            <div><strong>{{ m.productNames.map(name=>m.direction==='PRODUCTION_TO_WAREHOUSE'&&!m.returnMovement?`${name}（成品）`:name).join('、') }}</strong><span>{{ showDateTime(m.movementTime) }}<b v-if="m.returnMovement" class="return-tag">退料</b></span><small>{{ m.recordNo }}<template v-if="m.status!=='ACTIVE'"> · 已作废，不计入进度</template></small></div>
            <b>{{ m.totalCartons }} 箱 · <template v-if="m.quantityUnit">{{ m.unknownQuantityCount ? '已知 ' : '' }}{{ m.totalQuantity.toLocaleString() }} {{ m.quantityUnit }}</template><template v-else>查看数量明细</template></b><span class="arrow" aria-hidden="true">›</span>
          </RouterLink>
        </section></div>
      </section>
      <section v-if="authenticated && !editing" class="section actions" :aria-busy="changingStatus || deleting"><button class="btn btn-secondary" :disabled="changingStatus || deleting" @click="editing=true">编辑任务</button><button v-if="task.status==='PREPARING'||task.status==='IN_PROGRESS'" class="btn btn-primary" :disabled="changingStatus || deleting" @click="setStatus('COMPLETED')">标记已完成</button><button v-else class="btn btn-secondary" :disabled="changingStatus || deleting" @click="setStatus('IN_PROGRESS')">重新开启任务</button><button class="btn btn-danger delete-button" :disabled="changingStatus || deleting" @click="deleteTask">{{ deleting ? '正在删除…' : '删除任务' }}</button></section>
      <dialog ref="dialog" class="history-dialog" aria-labelledby="history-title" @click="($event.target===dialog)&&dialog?.close()"><template v-if="selected"><header><div><span class="hint">任务流转明细</span><h2 id="history-title">{{ selected.productName }}{{ selected.finished ? '（成品）' : '' }}</h2></div><button class="btn btn-secondary" autofocus @click="dialog?.close()">关闭</button></header><TaskMaterialHistory :movements="task.movements" :product-id="selected.productId" :product-name="selected.productName" :finished="selected.finished" /></template></dialog>
    </template>
  </div>
</template>
<style scoped>
.back-link{display:inline-flex;align-items:center;min-height:40px;margin-bottom:12px;color:var(--blue);font-size:14px;font-weight:650}.back-link:focus-visible{outline:2px solid var(--blue);outline-offset:3px}
.direction-switch{display:flex;gap:4px;padding:4px;margin-bottom:12px;border-radius:10px;background:#e7edf0}.direction-switch button{flex:1;min-width:0;min-height:42px;padding:8px;border:0;border-radius:7px;background:transparent;color:var(--muted);font-size:13px;font-weight:650;cursor:pointer}.direction-switch button[aria-pressed="true"]{background:white;color:var(--blue);box-shadow:0 1px 4px rgba(23,74,104,.12)}.direction-switch button:focus-visible{outline:2px solid var(--blue);outline-offset:2px}.direction-switch small{margin-left:5px;font-variant-numeric:tabular-nums}
.task-head .status{margin-bottom:10px}.page-title{overflow-wrap:anywhere}.progress-pair{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-bottom:18px}.progress-card{border:1px solid var(--line);border-radius:12px;padding:18px 20px;background:#fff;min-width:0}.progress-card h2{font-size:14px;margin:0 0 10px;font-weight:650}.progress-card>strong{display:block;font-family:Consolas,monospace;font-size:38px;letter-spacing:-1.5px;line-height:1.2;font-variant-numeric:tabular-nums}.progress-card strong small{font-size:19px;margin-left:4px;font-weight:400}.progress-card p{font-size:11px;line-height:1.5;margin:10px 0 0;color:var(--muted)}.material-progress{color:#52626b}.production-progress{background:#174a68;border-color:#174a68;color:white}.production-progress p{color:#c9dfe9}.progress-track{height:5px;border-radius:5px;background:#e5ecee;overflow:hidden;margin-top:14px}.progress-track i{display:block;height:100%;background:#78928f}.production-progress .progress-track{background:#39677f}.production-progress .progress-track i{background:#82decc}.output-heading{margin-bottom:14px}.materials-heading{margin-bottom:16px}.calculation-note{font-size:12px;color:var(--muted);line-height:1.7;border-top:1px solid #e5ecee;margin-top:18px;padding-top:12px}.calculation-note summary{cursor:pointer}.remark{padding:12px;background:#e9eef0;border-radius:8px}.movement-groups{display:grid;gap:12px}.movement-group{border:1px solid var(--line);border-radius:12px;background:white;overflow:hidden}.group-heading{display:flex;justify-content:space-between;flex-wrap:wrap;gap:6px;padding:13px 14px;background:#eef4f6}.group-heading h3{font-size:14px;margin:0;color:var(--blue)}.group-heading>span,.group-empty{font-size:12px;color:var(--muted)}.group-empty{padding:4px 14px}.movement-row{display:grid;grid-template-columns:minmax(0,1fr) auto auto;align-items:center;gap:12px;padding:14px;border-top:1px solid #edf0f2}.movement-row>div{display:grid;gap:5px;min-width:0}.movement-row strong{font-size:14px;overflow-wrap:anywhere}.movement-row span,.movement-row small{color:var(--muted);font-size:12px}.movement-row>b{font-size:13px}.movement-row .arrow{font-size:24px}.return-tag{margin-left:8px;color:var(--orange);font-size:11px}.actions{display:flex;flex-wrap:wrap;gap:9px}.delete-button{margin-left:auto}.form-alert{color:var(--red)}.history-dialog{width:min(640px,calc(100vw - 24px));max-height:85dvh;overflow-y:auto;border:1px solid var(--line);border-radius:16px;padding:20px;color:var(--ink);background:#f5f8f9}.history-dialog::backdrop{background:rgba(14,31,42,.48)}.history-dialog header{display:flex;justify-content:space-between;gap:12px;align-items:center;margin-bottom:20px}.history-dialog header>div{min-width:0}.history-dialog h2{font-size:20px;margin:5px 0 0;overflow-wrap:anywhere}.history-dialog header button{flex-shrink:0}@media(max-width:520px){.progress-pair{gap:9px}.progress-card{padding:14px 12px}.progress-card>strong{font-size:32px}.movement-row{grid-template-columns:1fr auto}.movement-row>b{grid-column:1}.movement-row .arrow{grid-column:2;grid-row:1/3}.history-dialog{padding:16px}.delete-button{margin-left:0}}
</style>
