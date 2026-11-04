<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import type { ProductionTask } from '../types';
import { startProductionTask } from '../api/productionTaskActions';
import { errorMessage } from '../api/http';
import TaskFindingChecklist from './TaskFindingChecklist.vue';

const props = defineProps<{ task: ProductionTask; authenticated: boolean }>();
const emit = defineEmits<{ saved: [task: ProductionTask]; editing: [value: boolean] }>();
const dirty = ref(false), findingBusy = ref(false), starting = ref(false), message = ref('');
const ready = computed(() => props.task.targets.length > 0 && props.task.targets.every(target => target.found) && !dirty.value && !findingBusy.value);
watch(() => dirty.value || findingBusy.value || starting.value, value => emit('editing', value), { flush: 'sync' });
onBeforeUnmount(() => emit('editing', false));
async function start() {
  if (!props.authenticated || !ready.value || starting.value) return;
  starting.value = true;
  message.value = '';
  try { emit('saved', await startProductionTask(props.task.id)); }
  catch (error) { message.value = errorMessage(error); }
  finally { starting.value = false; }
}
</script>

<template>
  <article class="preview-row">
    <header class="preview-heading">
      <RouterLink :to="`/production-tasks/${task.id}`" class="preview-info">
        <strong>{{ task.name }}</strong>
        <span>计划日期 {{ task.plannedDate }}<template v-if="task.orderNo"> · 订单号 {{ task.orderNo }}</template><template v-if="task.feishuNo"> · 飞书号 {{ task.feishuNo }}</template></span>
        <span v-if="task.plannedQuantity || task.productBatch">{{ task.plannedQuantity ? `计划数量 ${task.plannedQuantity.toLocaleString()}` : '' }}{{ task.plannedQuantity && task.productBatch ? ' · ' : '' }}{{ task.productBatch ? `产品批次 ${task.productBatch}` : '' }}</span>
      </RouterLink>
      <RouterLink :to="`/production-tasks/${task.id}`" class="btn btn-secondary">详情</RouterLink>
    </header>
    <TaskFindingChecklist :task="task" :authenticated="authenticated" :disabled="starting" @saved="emit('saved', $event)" @dirty="dirty=$event" @busy="findingBusy=$event" />
    <p v-if="message" class="form-alert" role="alert">{{ message }}</p>
    <div v-if="authenticated && ready" class="preview-start">
      <span class="hint">全部物料已确认找到，可以开始生产。</span>
      <button type="button" class="btn btn-primary" :disabled="starting" @click="start">{{ starting ? '正在开始生产…' : '开始生产' }}</button>
    </div>
  </article>
</template>

<style scoped>
.preview-row{min-width:0;padding:16px;background:var(--panel);border:1px solid var(--line);border-radius:var(--radius)}
.preview-heading,.preview-start{display:flex;align-items:center;justify-content:space-between;gap:12px}
.preview-info{display:grid;gap:5px;min-width:0;color:inherit;text-decoration:none}.preview-info strong{font-size:15px;overflow-wrap:anywhere}.preview-info span{font-size:12px;color:var(--muted);overflow-wrap:anywhere}
.preview-heading>.btn,.preview-start>.btn{flex-shrink:0}.preview-start{margin-top:14px}.form-alert{color:var(--red)}
@media(max-width:600px){.preview-row{padding:12px}.preview-start{align-items:flex-start;flex-wrap:wrap}}
</style>
