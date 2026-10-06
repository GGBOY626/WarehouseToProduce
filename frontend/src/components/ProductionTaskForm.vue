<script setup lang="ts">
import { ref } from "vue";
import { productionTasksApi, productsApi } from "../api";
import { errorMessage } from "../api/http";
import type { ProductionTask } from "../types";
import { today } from "../utils/time";
import SearchPicker from "./SearchPicker.vue";

const props = defineProps<{ task?: ProductionTask }>();
const emit = defineEmits<{ saved: [task: ProductionTask]; cancel: [] }>();
let nextKey = 0;
const newTarget = () => ({ key: nextKey++, productId: undefined as number | undefined, targetQuantity: 0, baseUnit: "" });
const form = ref({
  targets: props.task?.targets.map(t => ({ key: nextKey++, productId: t.productId as number | undefined, targetQuantity: t.targetQuantity, baseUnit: t.baseUnit || "" })) || [newTarget()],
  plannedDate: props.task?.plannedDate || today(), batchNo: props.task?.batchNo || "", remarks: props.task?.remarks || ""
});
const saving = ref(false), message = ref("");
async function save() {
  if (saving.value) return;
  const targets = form.value.targets;
  if (!form.value.plannedDate || !form.value.batchNo.trim() || targets.some(t => !t.productId || !Number.isSafeInteger(t.targetQuantity) || t.targetQuantity <= 0)) {
    message.value = "请填写每个目标产品及大于零的整数数量，并填写计划日期和生产批次。"; return;
  }
  if (new Set(targets.map(t => t.productId)).size !== targets.length) {
    message.value = "同一个目标产品不能重复添加。"; return;
  }
  saving.value = true; message.value = "";
  try {
    const payload = { ...form.value, targets: targets.map(t => ({ productId: t.productId, targetQuantity: t.targetQuantity })) };
    const result = props.task ? await productionTasksApi.update(props.task.id, payload) : await productionTasksApi.create(payload);
    emit("saved", result);
  } catch (e) { message.value = errorMessage(e); } finally { saving.value = false; }
}
</script>

<template>
  <form class="card card-pad task-form" @submit.prevent="save">
    <p class="hint">按需添加成品、外盒或内盒，每项目标分别填写数量。</p>
    <fieldset :disabled="saving">
      <div v-for="(target, index) in form.targets" :key="target.key" class="target-row">
        <SearchPicker v-model="target.productId" :label="`目标产品 ${index + 1}`" placeholder="搜索成品、外盒或内盒"
          :load="q => productsApi.search(q, !!task, 'PRODUCTION_TO_WAREHOUSE')"
          :display="p => `${p.name} · ${p.materialCode}`" @select="p => target.baseUnit = p?.baseUnit || ''" />
        <label class="field"><span>目标数量 * {{ target.baseUnit ? `（${target.baseUnit}）` : '' }}</span>
          <input class="input" type="number" min="1" step="1" required v-model.number="target.targetQuantity" />
        </label>
        <button type="button" class="btn btn-secondary" :disabled="form.targets.length === 1" :aria-label="`移除目标产品 ${index + 1}`" @click="form.targets.splice(index, 1)">移除</button>
      </div>
      <button type="button" class="btn btn-secondary" @click="form.targets.push(newTarget())">＋ 添加目标产品</button>
      <div class="grid-2">
        <label class="field"><span>计划日期 *</span><input class="input" type="date" required v-model="form.plannedDate" /></label>
        <label class="field"><span>生产批次 *</span><input class="input mono" maxlength="100" required v-model.trim="form.batchNo" /></label>
      </div>
      <label class="field"><span>备注</span><textarea class="textarea" maxlength="1000" v-model.trim="form.remarks" /></label>
    </fieldset>
    <p v-if="message" class="form-alert" role="alert">{{ message }}</p>
    <div class="form-actions"><button class="btn btn-primary" :disabled="saving">{{ saving ? '保存中…' : task ? '保存修改' : '创建生产任务' }}</button><button type="button" class="btn btn-secondary" :disabled="saving" @click="emit('cancel')">取消</button></div>
  </form>
</template>

<style scoped>
.task-form,fieldset{display:grid;gap:16px}.task-form{margin-top:16px}fieldset{padding:0;border:0;min-width:0}.target-row{display:grid;grid-template-columns:minmax(0,2fr) minmax(0,1fr) auto;gap:12px;align-items:end;border-bottom:1px solid #e5ecee;padding-bottom:16px}.target-row>*{min-width:0}fieldset>.btn{justify-self:start}.form-actions{display:flex;gap:10px}.form-alert{color:#8f1d25}.hint{margin:0}@media(max-width:600px){.target-row{grid-template-columns:minmax(0,1fr) auto}.target-row>:first-child{grid-column:1/-1}}
</style>
