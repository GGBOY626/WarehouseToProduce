<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref } from "vue";
import { useRoute } from "vue-router";
import { productionTasksApi } from "../api";
import { errorMessage } from "../api/http";
import { useAuth } from "../auth";
import type { ProductionTask, ProductionTaskStatus } from "../types";
import { directionLabel } from "../utils/labels";
import ProductionTaskForm from "../components/ProductionTaskForm.vue";
import ProductionTaskOutput from "../components/ProductionTaskOutput.vue";
import ProductionTaskTargets from "../components/ProductionTaskTargets.vue";
import { showDateTime } from "../utils/time";
const id=Number(useRoute().params.id),task=ref<ProductionTask>(),message=ref("");const{authenticated}=useAuth();
const editing=ref(false),changingStatus=ref(false);
function saved(value:ProductionTask){task.value=value;editing.value=false}
const labels:Record<ProductionTaskStatus,string>={PREPARING:'进行中',IN_PROGRESS:'进行中',COMPLETED:'已完成',CANCELLED:'已取消'};
async function load(){try{task.value=await productionTasksApi.detail(id)}catch(e){message.value=errorMessage(e)}}
async function setStatus(status:ProductionTaskStatus){if(changingStatus.value)return;changingStatus.value=true;message.value="";try{task.value=await productionTasksApi.status(id,status)}catch(e){message.value=errorMessage(e)}finally{changingStatus.value=false}}
let refreshTimer: ReturnType<typeof setInterval>;
function refreshVisible(){ if(document.visibilityState === 'visible' && !editing.value) void load(); }
onMounted(() => { void load(); refreshTimer=setInterval(refreshVisible,15000); window.addEventListener('focus',refreshVisible); });
onBeforeUnmount(() => { clearInterval(refreshTimer); window.removeEventListener('focus',refreshVisible); });
</script>
<template><div class="page"><p v-if="message" class="form-alert">{{message}}</p><div v-if="!task" class="card empty">正在读取生产任务…</div><template v-else><div class="task-head"><div><span class="status neutral">{{labels[task.status]}}</span><h1 class="page-title">{{task.name}}</h1><p class="page-lead mono">{{task.batchNo}} · 计划 {{task.plannedDate}}</p></div><div class="completion"><strong>{{task.progressPercent}}%</strong><span>备料进度</span></div></div><ProductionTaskForm v-if="editing" :task="task" @saved="saved" @cancel="editing=false" /><section v-else class="card card-pad"><ProductionTaskOutput v-if="task.output" :output="task.output" class="output-panel" />
<p v-else class="hint">此任务尚未设置出货物料及包装，编辑任务后即可关联成品回仓。</p>
<ProductionTaskTargets :targets="task.targets" /><p class="hint progress-note">备料进度按需求总数计算，不同批次合计。净发料 = 已发料 − 已退料；生产完成度 = 回仓折算数量 ÷ 主料实际净发料，与计划需求不同。包装物料的预计耗用按计划配比折算，实际损耗需在生产结束后核对。仅统计关联的有效记录，任务由人工标记完成。</p></section><p v-if="task.remarks" class="remark">{{task.remarks}}</p>
<section class="section"><div class="section-head"><h2 class="section-title">关联流转记录</h2><span class="hint">{{task.movements.length}} 条 · {{task.returnMovementCount}} 条退料</span></div><div v-if="!task.movements.length" class="card empty">新增流转记录时选择此任务，记录会显示在这里。</div><div v-else class="movement-list"><RouterLink v-for="m in task.movements" :key="m.id" :to="`/movements/${m.id}`" class="movement-row card"><div><strong>{{m.productNames.join("、")}}</strong><span>{{directionLabel(m.direction)}}<template v-if="m.returnMovement"> · 退回</template> · {{showDateTime(m.movementTime)}}</span><small v-if="m.status!=='ACTIVE'">已作废 · 不计入进度</small></div><b>{{m.totalCartons}} 箱 · <template v-if="m.quantityUnit">{{m.unknownQuantityCount ? '已知 ' : ''}}{{m.totalQuantity.toLocaleString()}} {{m.quantityUnit}}</template><template v-else>查看数量明细</template></b><span class="arrow">›</span></RouterLink></div></section>
<section v-if="authenticated && !editing" class="section actions" :aria-busy="changingStatus"><button class="btn btn-secondary" :disabled="changingStatus" @click="editing=true">编辑任务</button><button v-if="task.status==='PREPARING'||task.status==='IN_PROGRESS'" class="btn btn-primary" :disabled="changingStatus" @click="setStatus('COMPLETED')">标记已完成</button><button v-else class="btn btn-secondary" :disabled="changingStatus" @click="setStatus('IN_PROGRESS')">重新开启任务</button></section></template></div></template>
<style scoped>.output-panel{margin-bottom:20px;padding-bottom:18px;border-bottom:1px solid #e5ecee}.progress-note{margin:16px 0 0}.page-title{overflow-wrap:anywhere}.task-head{display:flex;justify-content:space-between;gap:16px;align-items:flex-start}.completion{text-align:right;color:#267566}.completion strong{display:block;font-size:32px;line-height:1}.completion span{font-size:12px}.overview{position:relative;display:grid;grid-template-columns:repeat(3,1fr);gap:12px;overflow:hidden}.overview>div{display:grid;gap:5px}.overview small{color:#66747c}.overview b{font-size:17px}.bar{position:absolute;left:0;right:0;bottom:0;height:5px;background:#e5ecee}.bar i{display:block;height:100%;background:#267566}.remark{padding:12px;background:#f3f7f8;border-radius:8px}.movement-list{display:grid;gap:9px}.movement-row{display:grid;grid-template-columns:minmax(0,1fr) auto auto;align-items:center;gap:12px;padding:12px 14px}.movement-row>div{display:grid;gap:3px}.movement-row span,.movement-row small{color:#66747c;font-size:12px}.movement-row>b{font-size:13px}.arrow{font-size:24px}.actions{display:flex;flex-wrap:wrap;gap:9px}.form-alert{color:#8f1d25}@media(max-width:520px){.overview{grid-template-columns:1fr}.movement-row{grid-template-columns:1fr auto}.movement-row>b{grid-column:1}.arrow{grid-column:2;grid-row:1/3}}</style>
