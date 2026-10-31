<script setup lang="ts">
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from "vue";
import { productionTasksApi } from "../api";
import { startProductionTask } from "../api/productionTaskActions";
import { errorMessage } from "../api/http";
import { useAuth } from "../auth";
import type { ProductionTask } from "../types";
import ProductionTaskForm from "../components/ProductionTaskForm.vue";
import ProductionTaskCard from "../components/ProductionTaskCard.vue";
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import { readTaskListState, saveTaskListState } from '../utils/taskListState';
import { activeTask, attentionFirst } from '../utils/taskAttention';
const route=useRoute(), router=useRouter();
const overviewEntry=route.query.status==='active';
const savedListState = readTaskListState();
const expandedTaskIds = ref<number[]>(savedListState.expandedTaskIds ?? []);
function toggleShortages(id: number) {
  expandedTaskIds.value = expandedTaskIds.value.includes(id) ? expandedTaskIds.value.filter(value => value !== id) : [...expandedTaskIds.value, id];
}
const { authenticated } = useAuth();
const loading=ref(true);
const startingPreviewId=ref<number>();
const tasks = ref<ProductionTask[]>([]), message = ref(""), showForm = ref(false);
const active = computed(() => attentionFirst(tasks.value.filter(activeTask)));
const finished = computed(() => tasks.value.filter(t => t.status === 'COMPLETED'));
const previews = computed(() => tasks.value.filter(t => t.status === 'PREVIEW').sort((a,b) => a.createdAt.localeCompare(b.createdAt) || a.id-b.id));
const showFinished = ref(false);
const showPreviews = ref(false);
const groups = computed(() => [showPreviews.value ? { title: '任务预告', tasks: previews.value } : showFinished.value ? { title: '已完成的任务', tasks: finished.value } : { title: '进行中的任务', tasks: active.value }]);
async function load() { try { tasks.value = await productionTasksApi.list(); message.value=""; } catch (e) { message.value = errorMessage(e); } finally { loading.value=false; } }
function taskSaved(task:ProductionTask){showForm.value=false;showFinished.value=false;showPreviews.value=task.status==='PREVIEW';void load()}
async function startPreview(id:number){if(startingPreviewId.value)return;startingPreviewId.value=id;message.value="";try{const updated=await startProductionTask(id);tasks.value=tasks.value.map(task=>task.id===id?updated:task);showPreviews.value=false;showFinished.value=false}catch(e){message.value=errorMessage(e)}finally{startingPreviewId.value=undefined}}
let refreshTimer: ReturnType<typeof setInterval>;
function refreshVisible(){ if(document.visibilityState === 'visible' && !showForm.value) void load(); }
onBeforeRouteLeave(() => { saveTaskListState({ scrollY: window.scrollY, expandedTaskIds: expandedTaskIds.value }); });
let mounted = true;
onMounted(async () => { if(overviewEntry)await router.replace({query:{...route.query,status:undefined,attention:undefined}}); await load(); if (!mounted) return; await nextTick(); window.scrollTo({ top: overviewEntry ? 0 : savedListState.scrollY, behavior: 'instant' }); refreshTimer=setInterval(refreshVisible,15000); window.addEventListener('focus',refreshVisible); });
onBeforeUnmount(() => { mounted=false; clearInterval(refreshTimer); window.removeEventListener('focus',refreshVisible); });
</script>
<template>
  <div class="page task-page">
    <div class="page-heading"><div><h1 class="page-title">生产任务</h1><p class="page-lead">查看任务备料情况、分批回仓产量及生产完成率。</p></div><button v-if="authenticated" class="btn btn-primary" @click="showForm = !showForm">{{ showForm ? '收起' : '＋ 新建任务' }}</button></div>
    <p v-if="message" class="form-alert" role="alert">{{ message }}</p>
    <ProductionTaskForm v-if="showForm && authenticated" @saved="taskSaved" @cancel="showForm = false" />
    <div class="task-toolbar">
      <div class="task-filter" role="group" aria-label="任务状态">
        <button type="button" :aria-pressed="!showFinished&&!showPreviews" @click="showFinished=false;showPreviews=false">进行中 <span>{{ active.length }}</span></button>
        <button type="button" :aria-pressed="showPreviews" @click="showFinished=false;showPreviews=true">任务预告 <span>{{ previews.length }}</span></button>
        <button type="button" :aria-pressed="showFinished" @click="showFinished=true;showPreviews=false">已完成 <span>{{ finished.length }}</span></button>
      </div>
    </div>
    <section v-for="group in groups" :key="group.title" class="task-section" :aria-busy="loading" aria-labelledby="task-group-title">
      <div class="section-head"><h2 id="task-group-title" class="section-title">{{ group.title }}</h2><span class="task-count" role="status">{{ loading ? '读取中…' : `${group.tasks.length} 个任务` }}</span></div>
      <div v-if="loading" class="card empty" role="status">正在读取任务…</div>
      <div v-else-if="!group.tasks.length" class="card empty">暂无{{ group.title }}。</div>
      <div v-else-if="showPreviews" class="preview-list">
        <article v-for="task in previews" :key="task.id" class="preview-row">
          <RouterLink :to="`/production-tasks/${task.id}`" class="preview-info"><strong>{{task.name}}</strong><span>计划日期 {{task.plannedDate}}<template v-if="task.orderNo"> · 订单号 {{task.orderNo}}</template><template v-if="task.feishuNo"> · 飞书号 {{task.feishuNo}}</template></span><span v-if="task.plannedQuantity || task.productBatch">{{task.plannedQuantity ? `计划数量 ${task.plannedQuantity.toLocaleString()}` : ''}}{{task.plannedQuantity&&task.productBatch?' · ':''}}{{task.productBatch ? `产品批次 ${task.productBatch}` : ''}}</span></RouterLink>
          <button v-if="authenticated" type="button" class="btn btn-primary" :disabled="startingPreviewId!=null" @click="startPreview(task.id)">{{startingPreviewId===task.id?'开始中…':'开始生产'}}</button>
        </article>
      </div>
      <div v-else class="task-list">
        <ProductionTaskCard v-for="task in group.tasks" :key="task.id" :task="task" :expanded="expandedTaskIds.includes(task.id)" @toggle="toggleShortages(task.id)" />
      </div>
    </section>
  </div>
</template>
<style scoped>
.task-page{max-width:1040px;padding-top:30px}
.page-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:16px;margin-bottom:22px}
.page-heading>.btn{flex-shrink:0}.page-title{font-size:26px;letter-spacing:0}.page-lead{margin:8px 0 0;font-size:13px;line-height:1.6}
.task-toolbar{display:flex;align-items:center;justify-content:flex-start;flex-wrap:wrap;gap:12px;padding:14px 16px;background:var(--panel);border:1px solid var(--line);border-radius:var(--radius)}
.task-filter{display:inline-flex;gap:4px;padding:4px;background:#edf0f2;border-radius:9px;max-width:100%}
.task-filter button{display:flex;align-items:center;justify-content:center;gap:10px;min-height:40px;padding:8px 18px;border:1px solid transparent;border-radius:6px;background:transparent;color:var(--muted);font-size:13px;font-weight:650;cursor:pointer}
.task-filter button span{padding:1px 6px;min-width:24px;border-radius:4px;font-size:12px;font-variant-numeric:tabular-nums;background:rgba(23,33,38,.05)}
.task-filter button[aria-pressed="true"]{background:var(--panel);color:var(--blue);border-color:var(--line);box-shadow:0 1px 3px rgba(20,39,50,.07)}
.task-filter button[aria-pressed="true"] span{background:var(--soft-blue)}
.task-filter button:hover{color:var(--blue);background:var(--soft-blue)}
.task-filter button:active{border-color:var(--blue2)}
.task-filter button:focus-visible{outline:2px solid var(--blue2);outline-offset:2px}
.task-section{margin-top:24px}.section-head{margin-bottom:12px}.section-title{font-size:15px;font-weight:650}.task-count{color:var(--muted);font-size:12px;font-variant-numeric:tabular-nums}
.task-list,.preview-list{display:grid;gap:10px}.preview-row{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:14px 16px;background:var(--panel);border:1px solid var(--line);border-radius:var(--radius)}.preview-info{display:grid;gap:5px;min-width:0;color:inherit;text-decoration:none}.preview-info strong{font-size:14px;overflow-wrap:anywhere}.preview-info span{font-size:12px;color:var(--muted);overflow-wrap:anywhere}.preview-row>.btn{flex-shrink:0}.form-alert{color:var(--red)}
@media(max-width:600px){.task-page{padding-top:20px}.page-heading{gap:10px;margin-bottom:18px}.page-title{font-size:23px}.page-heading>.btn{min-height:44px;padding:8px 12px;font-size:13px}.page-lead{max-width:28em;font-size:12px}.task-toolbar{padding:10px 12px;gap:5px}.task-filter{width:100%}.task-filter button{flex:1;min-height:42px;padding-inline:8px;gap:5px;font-size:12px}.task-section{margin-top:20px}.task-list{gap:12px}.preview-row{align-items:flex-start;padding:12px;gap:8px}.preview-row>.btn{min-height:40px;padding:7px 10px;font-size:12px}}
</style>
