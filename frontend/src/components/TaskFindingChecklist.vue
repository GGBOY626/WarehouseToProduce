<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { ProductionTask } from '../types';
import { saveTaskFindings } from '../api/productionTaskActions';
import { errorMessage } from '../api/http';
const props = defineProps<{ task: ProductionTask; authenticated: boolean; disabled: boolean }>();
const emit = defineEmits<{ saved: [task: ProductionTask]; dirty: [value: boolean]; busy: [value: boolean] }>();
const rows = ref<{ productId: number; found: boolean; foundLocation: string }[]>([]);
watch(() => props.task, task => { rows.value = task.targets.map(t => ({ productId: t.productId, found: !!t.found, foundLocation: t.foundLocation || '' })); }, { immediate: true });
const dirty = computed(() => rows.value.some((row, index) => row.found !== !!props.task.targets[index]?.found || row.foundLocation !== (props.task.targets[index]?.foundLocation || '')));
watch(dirty, value => emit('dirty', value), { immediate: true });
const foundCount = computed(() => rows.value.filter(row => row.found).length);
const saving = ref(false), message = ref('');
async function save() {
  if (saving.value || props.disabled) return;
  saving.value = true; emit('busy', true); message.value = '';
  try { emit('saved', await saveTaskFindings(props.task.id, rows.value.map(row => ({ ...row, foundLocation: row.foundLocation.trim() || null })))); }
  catch (error) { message.value = errorMessage(error); }
  finally { saving.value = false; emit('busy', false); }
}
</script>
<template>
  <form class="card card-pad finding-checklist" @submit.prevent="save">
    <h2 class="section-title">生产前找货确认 · {{ foundCount }} / {{ rows.length }}</h2>
    <p class="hint">逐项确认全部物料已找到并保存后，才能开始生产。位置备注可选。</p>
    <p v-if="message" class="error-text" role="alert">{{ message }}</p>
    <fieldset :disabled="!authenticated || disabled || saving">
      <div v-for="(row, index) in rows" :key="row.productId" class="finding-row" :class="{found: row.found}">
        <label class="finding-toggle"><input type="checkbox" v-model="row.found" /><span><strong>{{ task.targets[index]?.productName }}</strong><small>{{ row.found ? '已找到' : '尚未找到' }} · 需求 {{ task.targets[index]?.targetQuantity.toLocaleString() }} {{ task.targets[index]?.baseUnit }}</small></span></label>
        <label class="field"><span>位置备注（可选）</span><input class="input" v-model="row.foundLocation" maxlength="255" placeholder="例如：原料库 A 区 3 号架" /></label>
      </div>
      <button v-if="authenticated" class="btn btn-primary" :disabled="!dirty">{{ saving ? '保存中…' : '保存找货确认' }}</button>
    </fieldset>
    <p v-if="dirty" class="hint" role="status">找货确认尚未保存，请先保存。</p>
  </form>
</template>
<style scoped>
.finding-checklist{margin-top:18px;border-color:#d6aa6b}.finding-checklist fieldset{border:0;padding:0;margin:0;display:grid;gap:12px;min-width:0}.finding-row{display:grid;gap:12px;padding:14px;border:1px solid var(--line);border-radius:9px;background:#fffaf2}.finding-row.found{background:var(--soft-green)}.finding-toggle{display:flex;gap:10px;align-items:center;min-height:44px;cursor:pointer}.finding-toggle input{width:22px;height:22px;flex-shrink:0}.finding-toggle span{display:grid;gap:5px;overflow-wrap:anywhere}.finding-toggle small{color:var(--muted)}.finding-row .field>span{font-size:12px}.finding-checklist .btn{justify-self:start}
</style>
