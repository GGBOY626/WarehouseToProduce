<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue';
import { movementsApi, productionTasksApi } from '../api';
import type { Direction, ProductionTask } from '../types';
import { today } from '../utils/time';
import { activeTask, taskAlerts } from '../utils/taskAttention';
const emit=defineEmits<{ direction: [value: Direction] }>();
const outbound=ref<number>(), inbound=ref<number>(), tasks=ref<ProductionTask[]>();
const failed=ref(false), loading=ref(false);
const attention=computed(()=>tasks.value?.filter(t=>Boolean(taskAlerts(t))));
let alive=true;
async function load(){
  if(loading.value)return;
  loading.value=true;failed.value=false;
  const params={from:today(),to:today(),status:'ACTIVE' as const,page:0,size:1};
  const results=await Promise.allSettled([
    movementsApi.list({...params,direction:'WAREHOUSE_TO_PRODUCTION'}),
    movementsApi.list({...params,direction:'PRODUCTION_TO_WAREHOUSE'}), productionTasksApi.list()
  ]);
  if(!alive)return;
  outbound.value=results[0].status==='fulfilled'?results[0].value.totalElements:undefined;
  inbound.value=results[1].status==='fulfilled'?results[1].value.totalElements:undefined;
  tasks.value=results[2].status==='fulfilled'?results[2].value.filter(activeTask):undefined;
  failed.value=results.some(r=>r.status==='rejected');loading.value=false;
}
function refresh(){if(document.visibilityState==='visible')void load()}
let timer:ReturnType<typeof setInterval>;
onMounted(()=>{void load();timer=setInterval(refresh,60000);window.addEventListener('focus',refresh)});
onBeforeUnmount(()=>{alive=false;clearInterval(timer);window.removeEventListener('focus',refresh)});
</script>
<template>
  <section class="overview" aria-labelledby="overview-title" :aria-busy="loading">
    <div class="overview-heading"><h2 id="overview-title">今日概览</h2><span>{{ today() }} · 有效流转单</span></div>
    <div class="overview-grid card">
      <button @click="emit('direction','WAREHOUSE_TO_PRODUCTION')"><span>今日出库</span><b>{{ outbound ?? '—' }}<small>单</small></b></button>
      <button @click="emit('direction','PRODUCTION_TO_WAREHOUSE')"><span>今日回仓 / 入库</span><b>{{ inbound ?? '—' }}<small>单</small></b></button>
      <RouterLink to="/production-tasks?status=active"><span>进行中任务</span><b>{{ tasks?.length ?? '—' }}<small>项</small></b></RouterLink>
      <RouterLink to="/production-tasks?status=active&attention=1" :class="{attention:attention?.length}"><span>需关注任务</span><b>{{ attention?.length ?? '—' }}<small>项</small></b></RouterLink>
    </div>
    <p v-if="failed" class="overview-error" role="status">部分概览暂不可用 <button @click="load" :disabled="loading">重新读取</button></p>
    <details v-if="attention?.length" class="attention-list"><summary>{{ attention.length }} 项进行中任务需关注 <span>查看</span></summary><RouterLink v-for="task in attention.slice(0,3)" :key="task.id" :to="`/production-tasks/${task.id}`"><strong>{{ task.name }}</strong><small>{{ taskAlerts(task) }}</small></RouterLink><RouterLink v-if="attention.length>3" to="/production-tasks?status=active&attention=1">查看全部需关注任务 ›</RouterLink></details>
  </section>
</template>
<style scoped>
.overview{margin:14px 0}.overview-heading{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:8px}.overview-heading h2{font-size:14px;margin:0}.overview-heading>span{font-size:11px;color:var(--muted)}.overview-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));overflow:hidden}.overview-grid>a,.overview-grid>button{display:grid;grid-template-rows:32px auto;gap:3px;padding:10px 8px;border:0;background:white;text-align:left;cursor:pointer;min-width:0}.overview-grid>:not(:first-child){border-left:1px solid var(--line)}.overview-grid span{font-size:11px;color:var(--muted);line-height:1.5}.overview-grid b{font-size:24px;color:var(--blue);font-variant-numeric:tabular-nums}.overview-grid small{font-size:11px;font-weight:400;margin-left:4px;color:var(--muted)}.overview-grid .attention b{color:var(--orange)}.attention-list{font-size:12px;margin-top:8px}.attention-list summary{color:var(--orange);min-height:36px;padding:8px 0;cursor:pointer}.attention-list summary span{color:var(--muted);margin-left:8px}.attention-list a{display:grid;gap:4px;padding:10px 0;border-top:1px solid var(--line)}.attention-list small{color:var(--orange)}.overview-error{font-size:12px;color:var(--muted)}.overview-error button{border:0;background:none;color:var(--blue);min-height:36px;cursor:pointer}
</style>
