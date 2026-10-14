<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from "vue";
import { productionTasksApi } from "../api";
import { errorMessage } from "../api/http";
import { useAuth } from "../auth";
import type { ProductionTask, ProductionTaskStatus } from "../types";
import ProductionTaskForm from "../components/ProductionTaskForm.vue";
import ProductionTaskOutput from "../components/ProductionTaskOutput.vue";
import ProductionTaskTargets from "../components/ProductionTaskTargets.vue";
const { authenticated } = useAuth();
const tasks = ref<ProductionTask[]>([]), message = ref(""), showForm = ref(false);
const active = computed(() => tasks.value.filter(t => t.status === 'PREPARING' || t.status === 'IN_PROGRESS'));
const finished = computed(() => tasks.value.filter(t => t.status === 'COMPLETED'));
const showFinished = ref(false);
const groups = computed(() => [showFinished.value ? { title: '已完成的任务', tasks: finished.value } : { title: '进行中的任务', tasks: active.value }]);
const labels: Record<ProductionTaskStatus, string> = { PREPARING: '进行中', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消' };
async function load() { try { tasks.value = await productionTasksApi.list(); } catch (e) { message.value = errorMessage(e); } }
let refreshTimer: ReturnType<typeof setInterval>;
function refreshVisible(){ if(document.visibilityState === 'visible' && !showForm.value) void load(); }
onMounted(() => { void load(); refreshTimer=setInterval(refreshVisible,15000); window.addEventListener('focus',refreshVisible); });
onBeforeUnmount(() => { clearInterval(refreshTimer); window.removeEventListener('focus',refreshVisible); });
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
          <div class="task-info"><span class="task-status">{{ labels[task.status] }}</span><strong>{{ task.name }}</strong><small>{{ task.batchNo }} · 计划 {{ task.plannedDate }}</small></div>
          <div class="target"><b>{{ task.progressPercent }}%</b><span>备料进度</span></div>
          <ProductionTaskOutput v-if="task.output" class="task-output" :output="task.output" />
          <ProductionTaskTargets class="task-targets" :targets="task.targets" />
        </RouterLink>
      </div>
    </section>
  </div>
</template>
<style scoped>
.task-filter{display:flex;gap:6px;margin-top:16px}.task-filter button{border:0;border-radius:6px;background:transparent;color:#66747c;padding:7px 12px;font:inherit;font-size:13px;cursor:pointer}.task-filter button[aria-pressed="true"]{background:#e8f0f2;color:#174a68;font-weight:700}.task-filter small{margin-left:5px}
.task-output{grid-column:1/-1;padding-top:12px;border-top:1px solid #e5ecee}.page-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}.page-heading>.btn{flex-shrink:0}.task-list{display:grid;gap:10px}.task-card{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:14px;padding:16px}.task-info{display:grid;gap:4px;min-width:0}.task-info strong{font-size:17px;overflow-wrap:anywhere}.task-info small,.target span{color:#66747c;font-size:12px}.task-status{width:max-content;color:#174a68;font-size:11px;font-weight:750}.target{display:grid;text-align:right;align-content:center}.target b{font-variant-numeric:tabular-nums;font-size:22px;color:#267566}.task-targets{grid-column:1/-1;padding-top:12px;border-top:1px solid #e5ecee}.form-alert{color:#8f1d25}
</style>
