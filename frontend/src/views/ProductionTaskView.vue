<script setup lang="ts">
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from "vue";
import { productionTasksApi } from "../api";
import { errorMessage } from "../api/http";
import { useAuth } from "../auth";
import type { ProductionTask } from "../types";
import ProductionTaskForm from "../components/ProductionTaskForm.vue";
import ProductionTaskCard from "../components/ProductionTaskCard.vue";
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import { readTaskListState, saveTaskListState } from '../utils/taskListState';
import { activeTask, attentionFirst, taskAlerts as alerts } from '../utils/taskAttention';
const route=useRoute(), router=useRouter();
const overviewEntry=route.query.status==='active';
const savedListState = readTaskListState();
const expandedTaskIds = ref<number[]>(savedListState.expandedTaskIds ?? []);
function toggleShortages(id: number) {
  expandedTaskIds.value = expandedTaskIds.value.includes(id) ? expandedTaskIds.value.filter(value => value !== id) : [...expandedTaskIds.value, id];
}
const onlyAttention=ref(route.query.attention === '1' || (route.query.status == null && savedListState.onlyAttention === true));
const { authenticated } = useAuth();
const loading=ref(true);
const tasks = ref<ProductionTask[]>([]), message = ref(""), showForm = ref(false);
const active = computed(() => attentionFirst(tasks.value.filter(activeTask)));
const finished = computed(() => tasks.value.filter(t => t.status === 'COMPLETED'));
const showFinished = ref(route.query.status==='active'?false:savedListState.showFinished);
const groups = computed(() => [showFinished.value ? { title: '已完成的任务', tasks: finished.value } : { title: '进行中的任务', tasks: onlyAttention.value ? active.value.filter(t=>Boolean(alerts(t))) : active.value }]);
async function load() { try { tasks.value = await productionTasksApi.list(); message.value=""; } catch (e) { message.value = errorMessage(e); } finally { loading.value=false; } }
let refreshTimer: ReturnType<typeof setInterval>;
function refreshVisible(){ if(document.visibilityState === 'visible' && !showForm.value) void load(); }
onBeforeRouteLeave(() => { saveTaskListState({ showFinished: showFinished.value, onlyAttention: onlyAttention.value, scrollY: window.scrollY, expandedTaskIds: expandedTaskIds.value }); });
let mounted = true;
onMounted(async () => { if(overviewEntry)await router.replace({query:{...route.query,status:undefined,attention:undefined}}); await load(); if (!mounted) return; await nextTick(); window.scrollTo({ top: overviewEntry ? 0 : savedListState.scrollY, behavior: 'instant' }); refreshTimer=setInterval(refreshVisible,15000); window.addEventListener('focus',refreshVisible); });
onBeforeUnmount(() => { mounted=false; clearInterval(refreshTimer); window.removeEventListener('focus',refreshVisible); });
</script>
<template>
  <div class="page task-page">
    <div class="page-heading"><div><h1 class="page-title">生产任务</h1><p class="page-lead">查看任务备料情况、分批回仓产量及生产完成率。</p></div><button v-if="authenticated" class="btn btn-primary" @click="showForm = !showForm">{{ showForm ? '收起' : '＋ 新建任务' }}</button></div>
    <p v-if="message" class="form-alert" role="alert">{{ message }}</p>
    <ProductionTaskForm v-if="showForm && authenticated" @saved="showForm = false; load()" @cancel="showForm = false" />
    <div class="task-toolbar">
      <div class="task-filter" role="group" aria-label="任务状态">
        <button type="button" :aria-pressed="!showFinished" @click="showFinished=false">进行中 <span>{{ active.length }}</span></button>
        <button type="button" :aria-pressed="showFinished" @click="showFinished=true">已完成 <span>{{ finished.length }}</span></button>
      </div>
      <label v-if="!showFinished" class="attention-filter"><input type="checkbox" name="only-attention" v-model="onlyAttention" />只看需关注任务</label>
    </div>
    <section v-for="group in groups" :key="group.title" class="task-section" :aria-busy="loading" aria-labelledby="task-group-title">
      <div class="section-head"><h2 id="task-group-title" class="section-title">{{ group.title }}</h2><span class="task-count" role="status">{{ loading ? '读取中…' : `${group.tasks.length} 个任务` }}</span></div>
      <div v-if="loading" class="card empty" role="status">正在读取任务…</div>
      <div v-else-if="!group.tasks.length" class="card empty">暂无{{ group.title }}。</div>
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
.task-toolbar{display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:12px;padding:14px 16px;background:var(--panel);border:1px solid var(--line);border-radius:var(--radius)}
.task-filter{display:inline-flex;gap:4px;padding:4px;background:#edf0f2;border-radius:9px;max-width:100%}
.task-filter button{display:flex;align-items:center;justify-content:center;gap:10px;min-height:40px;padding:8px 18px;border:1px solid transparent;border-radius:6px;background:transparent;color:var(--muted);font-size:13px;font-weight:650;cursor:pointer}
.task-filter button span{padding:1px 6px;min-width:24px;border-radius:4px;font-size:12px;font-variant-numeric:tabular-nums;background:rgba(23,33,38,.05)}
.task-filter button[aria-pressed="true"]{background:var(--panel);color:var(--blue);border-color:var(--line);box-shadow:0 1px 3px rgba(20,39,50,.07)}
.task-filter button[aria-pressed="true"] span{background:var(--soft-blue)}
.task-filter button:hover{color:var(--blue);background:var(--soft-blue)}
.task-filter button:active{border-color:var(--blue2)}
.task-filter button:focus-visible{outline:2px solid var(--blue2);outline-offset:2px}
.attention-filter{display:flex;align-items:center;gap:9px;min-height:44px;padding:0 4px;color:var(--muted);font-size:13px;cursor:pointer}
.attention-filter input{margin:0;accent-color:var(--blue);width:17px;height:17px;cursor:pointer}
.attention-filter:hover,.attention-filter:has(input:checked){color:var(--blue)}
.attention-filter input:focus-visible{outline:2px solid var(--blue2);outline-offset:4px}
.task-section{margin-top:24px}.section-head{margin-bottom:12px}.section-title{font-size:15px;font-weight:650}.task-count{color:var(--muted);font-size:12px;font-variant-numeric:tabular-nums}
.task-list{display:grid;gap:14px}.form-alert{color:var(--red)}
@media(max-width:600px){.task-page{padding-top:20px}.page-heading{gap:10px;margin-bottom:18px}.page-title{font-size:23px}.page-heading>.btn{min-height:44px;padding:8px 12px;font-size:13px}.page-lead{max-width:28em;font-size:12px}.task-toolbar{padding:10px 12px;gap:5px}.task-filter{width:100%}.task-filter button{flex:1;min-height:42px;padding-inline:12px}.attention-filter{padding-left:4px}.task-section{margin-top:20px}.task-list{gap:12px}}
</style>
