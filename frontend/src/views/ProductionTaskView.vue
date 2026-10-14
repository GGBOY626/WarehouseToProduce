<script setup lang="ts">
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from "vue";
import { productionTasksApi } from "../api";
import { errorMessage } from "../api/http";
import { useAuth } from "../auth";
import type { ProductionTask, ProductionTaskStatus } from "../types";
import ProductionTaskForm from "../components/ProductionTaskForm.vue";
import { onBeforeRouteLeave } from 'vue-router';
import { readTaskListState, saveTaskListState } from '../utils/taskListState';
const savedListState = readTaskListState();
const { authenticated } = useAuth();
const tasks = ref<ProductionTask[]>([]), message = ref(""), showForm = ref(false);
const active = computed(() => tasks.value.filter(t => t.status === 'PREPARING' || t.status === 'IN_PROGRESS'));
const finished = computed(() => tasks.value.filter(t => t.status === 'COMPLETED'));
const showFinished = ref(savedListState.showFinished);
const groups = computed(() => [showFinished.value ? { title: '已完成的任务', tasks: finished.value } : { title: '进行中的任务', tasks: active.value }]);
const labels: Record<ProductionTaskStatus, string> = { PREPARING: '进行中', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消' };
async function load() { try { tasks.value = await productionTasksApi.list(); } catch (e) { message.value = errorMessage(e); } }
let refreshTimer: ReturnType<typeof setInterval>;
function refreshVisible(){ if(document.visibilityState === 'visible' && !showForm.value) void load(); }
onBeforeRouteLeave(() => { saveTaskListState({ showFinished: showFinished.value, scrollY: window.scrollY }); });
function productionPercent(task: ProductionTask) { return task.output?.quantityBasis ? task.output.productionPercent : null; }
function alerts(task: ProductionTask) {
  const notes: string[] = [];
  const missing = task.targets.filter(t => t.materialTarget && t.shortageQuantity > 0).length;
  if (missing) notes.push(`${missing} 种物料缺料`);
  if (task.targets.some(t => t.unknownQuantityCount > 0) || task.output?.unknownQuantityCount) notes.push('有数量待确认');
  if (task.targets.some(t => !t.materialTarget)) notes.push('备料配置待核对');
  return notes.join(' · ');
}
let mounted = true;
onMounted(async () => { await load(); if (!mounted) return; await nextTick(); window.scrollTo({ top: savedListState.scrollY, behavior: 'instant' }); refreshTimer=setInterval(refreshVisible,15000); window.addEventListener('focus',refreshVisible); });
onBeforeUnmount(() => { mounted=false; clearInterval(refreshTimer); window.removeEventListener('focus',refreshVisible); });
</script>
<template>
  <div class="page">
    <div class="page-heading"><div><h1 class="page-title">生产任务</h1><p class="page-lead">查看任务备料情况、分批回仓产量及生产完成率。</p></div><button v-if="authenticated" class="btn btn-primary" @click="showForm = !showForm">{{ showForm ? '收起' : '＋ 新建任务' }}</button></div>
    <p v-if="message" class="form-alert">{{ message }}</p>
    <ProductionTaskForm v-if="showForm && authenticated" @saved="showForm = false; load()" @cancel="showForm = false" />
    <div class="task-filter" aria-label="任务状态"><button :aria-pressed="!showFinished" @click="showFinished=false">进行中 <small>{{ active.length }}</small></button><button :aria-pressed="showFinished" @click="showFinished=true">已完成 <small>{{ finished.length }}</small></button></div>
    <section v-for="group in groups" :key="group.title" class="section">
      <div class="section-head"><h2 class="section-title">{{ group.title }}</h2><span class="hint">{{ group.tasks.length }} 个</span></div>
      <div v-if="!group.tasks.length" class="card empty">暂无{{ group.title }}。</div>
      <div v-else class="task-list">
        <RouterLink v-for="task in group.tasks" :key="task.id" :to="`/production-tasks/${task.id}`" class="task-card card">
          <div class="task-info"><strong>{{ task.name }}</strong><span class="task-status" :class="{ completed: task.status==='COMPLETED' }">{{ labels[task.status] }}</span></div>
          <div class="task-progress-pair">
            <div class="task-progress"><div><span>备料进度</span><b>{{ task.progressPercent }}%</b></div><div class="progress-track" role="progressbar" aria-label="备料进度" :aria-valuenow="task.progressPercent" :aria-valuemin="0" :aria-valuemax="100"><i :style="{width:`${task.progressPercent}%`}" /></div></div>
            <div class="task-progress production"><div><span>生产完成度</span><b>{{ productionPercent(task) == null ? '—' : `${productionPercent(task)}%` }}</b></div><div class="progress-track" role="progressbar" aria-label="生产完成度" :aria-valuenow="productionPercent(task) ?? undefined" :aria-valuetext="productionPercent(task) == null ? '暂无可计算数据' : undefined" :aria-valuemin="0" :aria-valuemax="100"><i :style="{width:`${productionPercent(task) || 0}%`}" /></div></div>
          </div>
          <p v-if="alerts(task)" class="task-alert">{{ alerts(task) }}</p>
        </RouterLink>
      </div>
    </section>
  </div>
</template>
<style scoped>
.task-filter{display:flex;gap:6px;margin-top:16px}.task-filter button{border:0;border-radius:6px;background:transparent;color:#66747c;padding:7px 12px;font:inherit;font-size:13px;cursor:pointer}.task-filter button[aria-pressed="true"]{background:#e8f0f2;color:#174a68;font-weight:700}.task-filter small{margin-left:5px}
.page-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}.page-heading>.btn{flex-shrink:0}.task-list{display:grid;gap:10px}.task-card{display:grid;gap:14px;padding:16px}.task-card:focus-visible{outline:2px solid var(--blue);outline-offset:3px}.task-info{display:flex;align-items:baseline;justify-content:space-between;gap:12px;min-width:0}.task-info strong{font-size:16px;overflow-wrap:anywhere}.task-status{flex-shrink:0;color:var(--blue);font-size:11px;font-weight:700;background:var(--soft-blue);padding:3px 7px;border-radius:5px}.task-status.completed{color:var(--green);background:var(--soft-green)}.task-progress-pair{display:grid;grid-template-columns:1fr 1fr;gap:20px}.task-progress>div:first-child{display:flex;justify-content:space-between;gap:6px;font-size:12px;color:var(--muted);margin-bottom:7px}.task-progress b{color:var(--green);font-variant-numeric:tabular-nums}.task-progress.production b{color:var(--blue)}.progress-track{height:4px;background:#e5ecee;border-radius:4px;overflow:hidden}.progress-track i{display:block;height:100%;background:var(--green)}.production .progress-track i{background:var(--blue)}.task-alert{margin:0;color:#9b3d17;font-size:12px}.form-alert{color:#8f1d25}@media(max-width:420px){.task-card{padding:14px}.task-progress-pair{gap:14px}}
</style>
