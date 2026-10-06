<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { productionTasksApi } from "../api";
import { errorMessage } from "../api/http";
import { useAuth } from "../auth";
import type { ProductionTask, ProductionTaskStatus } from "../types";
import ProductionTaskForm from "../components/ProductionTaskForm.vue";
import ProductionTaskTargets from "../components/ProductionTaskTargets.vue";
const { authenticated } = useAuth();
const tasks = ref<ProductionTask[]>([]), message = ref(""), showForm = ref(false);
const active = computed(() => tasks.value.filter(t => t.status === 'PREPARING' || t.status === 'IN_PROGRESS'));
const finished = computed(() => tasks.value.filter(t => t.status === 'COMPLETED' || t.status === 'CANCELLED'));
const groups = computed(() => [{ title: '进行中的任务', tasks: active.value }, { title: '已结束', tasks: finished.value }]);
const labels: Record<ProductionTaskStatus, string> = { PREPARING: '备料中', IN_PROGRESS: '生产中', COMPLETED: '已完成', CANCELLED: '已取消' };
async function load() { try { tasks.value = await productionTasksApi.list(); } catch (e) { message.value = errorMessage(e); } }
onMounted(load);
</script>
<template>
  <div class="page">
    <div class="page-heading"><div><h1 class="page-title">生产任务</h1><p class="page-lead">用目标数量串联发料、退料和成品入库。</p></div><button v-if="authenticated" class="btn btn-primary" @click="showForm = !showForm">{{ showForm ? '收起' : '＋ 新建任务' }}</button></div>
    <p v-if="message" class="form-alert">{{ message }}</p>
    <ProductionTaskForm v-if="showForm && authenticated" @saved="showForm = false; load()" @cancel="showForm = false" />
    <section v-for="group in groups" :key="group.title" class="section">
      <div class="section-head"><h2 class="section-title">{{ group.title }}</h2><span class="hint">{{ group.tasks.length }} 个</span></div>
      <div v-if="!group.tasks.length" class="card empty">暂无{{ group.title }}。</div>
      <div v-else class="task-list">
        <RouterLink v-for="task in group.tasks" :key="task.id" :to="`/production-tasks/${task.id}`" class="task-card card">
          <div class="task-info"><span class="task-status">{{ labels[task.status] }}</span><strong>{{ task.productName }}</strong><small>{{ task.batchNo }} · 计划 {{ task.plannedDate }}</small></div>
          <div class="target"><b>{{ task.progressPercent }}%</b><span>整体进度</span></div>
          <ProductionTaskTargets class="task-targets" :targets="task.targets" />
        </RouterLink>
      </div>
    </section>
  </div>
</template>
<style scoped>
.page-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}.page-heading>.btn{flex-shrink:0}.task-list{display:grid;gap:10px}.task-card{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:14px;padding:16px}.task-info{display:grid;gap:4px;min-width:0}.task-info strong{font-size:17px;overflow-wrap:anywhere}.task-info small,.target span{color:#66747c;font-size:12px}.task-status{width:max-content;color:#174a68;font-size:11px;font-weight:750}.target{display:grid;text-align:right;align-content:center}.target b{font-variant-numeric:tabular-nums;font-size:22px;color:#267566}.task-targets{grid-column:1/-1;padding-top:12px;border-top:1px solid #e5ecee}.form-alert{color:#8f1d25}
</style>
