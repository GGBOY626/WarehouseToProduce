<script setup lang="ts">
import { computed, ref } from "vue";
import { productionTasksApi, productsApi } from "../api";
import { errorMessage } from "../api/http";
import type { Product, ProductionTask } from "../types";
import { today } from "../utils/time";
import SearchPicker from "./SearchPicker.vue";
const props = defineProps<{ task?: ProductionTask }>();
const emit = defineEmits<{ saved: [task: ProductionTask]; cancel: [] }>();
let nextKey = 0;
const newTarget = () => ({ key: nextKey++, productId: undefined as number | undefined, productName: "", targetQuantity: 0, baseUnit: "" });
const form = ref({
  name: props.task?.name || "",
  targets: props.task?.targets.map(t => ({ key: nextKey++, productId: t.productId as number | undefined, productName: t.productName, targetQuantity: t.targetQuantity, baseUnit: t.baseUnit || "" })) || [newTarget()],
  plannedDate: props.task?.plannedDate || today(), batchNo: props.task?.batchNo || "", remarks: props.task?.remarks || ""
});
const outputEnabled = ref(!!props.task?.output);
const outputProductId = ref<number | undefined>(props.task?.output?.productId);
const outputQuantity = ref(props.task?.output?.targetQuantity || 0);
const outputUnit = ref(props.task?.output?.baseUnit || "");
const packagingEnabled = ref(!props.task?.output || !!props.task.output.boxesPerCarton);
const boxesPerCarton = ref<number | undefined>(props.task?.output?.boxesPerCarton ?? undefined);
const unitsPerBox = ref<number | undefined>(props.task?.output?.unitsPerBox ?? undefined);
const quantityBasis = ref<'BOX' | 'UNIT'>(props.task?.output?.quantityBasis || 'UNIT');
const primaryId = ref<number | undefined>(props.task?.output?.primaryMaterialProductId ?? undefined);
const primary = computed(() => form.value.targets.find(t => t.productId === primaryId.value));
const hasReceipts = computed(() => !!props.task?.movements.some(m => m.direction === 'PRODUCTION_TO_WAREHOUSE' && !m.returnMovement));
const packagingLocked = computed(() => hasReceipts.value && !!props.task?.output?.boxesPerCarton);
const saving = ref(false), message = ref("");
function selectMaterial(target: ReturnType<typeof newTarget>, p?: Product) {
  target.baseUnit = p?.baseUnit || ""; target.productName = p?.name || "";
}
function selectOutput(p?: Product) {
  outputUnit.value = p?.baseUnit || "";
  quantityBasis.value = p?.baseUnit === '盒' ? 'BOX' : 'UNIT';
}
async function loadOutputs(q: string): Promise<Product[]> {
  return (await productsApi.search(q, !!props.task, 'PRODUCTION_TO_WAREHOUSE')).filter(p => !p.quantityUnknown && (p.active || p.id === props.task?.output?.productId));
}
async function loadMaterials(q: string): Promise<Product[]> {
  return (await productsApi.search(q, !!props.task, 'WAREHOUSE_TO_PRODUCTION')).filter(p => p.active || props.task?.targets.some(t => t.productId === p.id));
}
async function save() {
  if (saving.value) return;
  if (!form.value.name.trim()) { message.value = "请填写任务名称。"; return; }
  const targets = form.value.targets;
  if (!form.value.plannedDate || !form.value.batchNo.trim() || targets.some(t => !t.productId || !Number.isSafeInteger(t.targetQuantity) || t.targetQuantity <= 0)) {
    message.value = "请填写每种物料的需求总数、计划日期和生产批次。"; return;
  }
  if (new Set(targets.map(t => t.productId)).size !== targets.length) { message.value = "同一种物料只填写一行需求总数，不需要按批次拆分。"; return; }
  if (outputEnabled.value && !outputProductId.value) { message.value = "请选择车间到仓库的成品物料。"; return; }
  if (outputEnabled.value && packagingEnabled.value && (!primary.value || !Number.isInteger(boxesPerCarton.value) || !Number.isInteger(unitsPerBox.value) || Number(boxesPerCarton.value) <= 0 || Number(unitsPerBox.value) <= 0 || Number(boxesPerCarton.value) * Number(unitsPerBox.value) > 2147483647)) {
    message.value = "请选择主料，并填写有效的每箱盒数和每盒数量。"; return;
  }
  if (outputEnabled.value && !packagingEnabled.value && (!Number.isSafeInteger(outputQuantity.value) || outputQuantity.value <= 0)) { message.value = "请填写原计划产量。"; return; }
  saving.value = true; message.value = "";
  try {
    const packed = outputEnabled.value && packagingEnabled.value;
    const payload = { ...form.value, outputProductId: outputEnabled.value ? outputProductId.value : null,
      targetOutputQuantity: outputEnabled.value && !packed ? outputQuantity.value : null,
      outputBoxesPerCarton: packed ? boxesPerCarton.value : null, outputUnitsPerBox: packed ? unitsPerBox.value : null,
      outputQuantityBasis: packed ? quantityBasis.value : null, primaryMaterialProductId: packed ? primaryId.value : null,
      targets: targets.map(t => ({ productId: t.productId, targetQuantity: t.targetQuantity })) };
    emit("saved", props.task ? await productionTasksApi.update(props.task.id, payload) : await productionTasksApi.create(payload));
  } catch (e) { message.value = errorMessage(e); } finally { saving.value = false; }
}
</script>
<template>
  <form class="card card-pad task-form" @submit.prevent="save">
    <p class="hint">每种物料填写需求总数即可，不同批次的发料合计计算，批次明细仍可查看。新任务保存后即为进行中。</p>
    <fieldset :disabled="saving">
      <label class="field"><span>任务名称 *</span><input class="input" v-model.trim="form.name" maxlength="100" required placeholder="例如：轻体粉第一批" /></label>
      <div v-for="(target, index) in form.targets" :key="target.key" class="target-row">
        <SearchPicker v-model="target.productId" :label="`所需物料 ${index + 1}`" placeholder="选择仓库发往车间的物料" :load="loadMaterials"
          :display="p => `${p.name} · ${p.materialCode}`" @select="p => selectMaterial(target, p)" />
        <label class="field"><span>需求总数 * {{ target.baseUnit ? `（${target.baseUnit}）` : '' }}</span><input class="input" type="number" min="1" step="1" required v-model.number="target.targetQuantity" /></label>
        <button type="button" class="btn btn-secondary" :disabled="form.targets.length === 1" :aria-label="`移除物料 ${index + 1}`" @click="form.targets.splice(index, 1)">移除</button>
      </div>
      <button type="button" class="btn btn-secondary" @click="form.targets.push(newTarget())">＋ 添加所需物料</button>
      <label class="output-choice"><input type="checkbox" v-model="outputEnabled" :disabled="hasReceipts" /> 关联出货物料（车间 → 仓库，可稍后设置）</label>
      <div v-if="outputEnabled" class="output-plan">
        <fieldset :disabled="hasReceipts">
          <SearchPicker v-model="outputProductId" label="出货物料 / 成品" placeholder="选择车间送回仓库的物料" :load="loadOutputs" :display="p => `${p.name} · ${p.materialCode}`" @select="selectOutput" />
        </fieldset>
        <label v-if="task?.output && !task.output.boxesPerCarton" class="output-choice"><input type="checkbox" v-model="packagingEnabled" /> 补充包装换算和生产完成度</label>
        <fieldset v-if="packagingEnabled" :disabled="packagingLocked">
          <label class="field"><span>计算完成度的主料 *</span><select class="select" v-model="primaryId" required><option :value="undefined">请选择，例如轻体粉</option><option v-for="target in form.targets.filter(t => t.productId)" :key="target.key" :value="target.productId">{{ target.productName }}（{{ target.baseUnit }}）</option></select></label>
          <div class="grid-2">
            <label class="field"><span>每箱装几盒 *</span><input class="input" type="number" min="1" step="1" required v-model.number="boxesPerCarton" placeholder="例如 28" /></label>
            <label class="field"><span>每盒装几个 * {{ primary?.baseUnit ? `（${primary.baseUnit}）` : '' }}</span><input class="input" type="number" min="1" step="1" required v-model.number="unitsPerBox" placeholder="例如 30" /></label>
          </div>
          <label class="field"><span>成品入库数量的计量口径 *</span><select class="select" v-model="quantityBasis"><option value="BOX">按盒登记（数量代表成品盒数）</option><option value="UNIT">按单件登记（数量代表条 / 个数）</option></select></label>
          <p class="hint">成品产品的基础单位为“{{ outputUnit || '待选择' }}”，请选择与它实际登记方式一致的口径。完成度按成品折算的{{ primary?.baseUnit || '主料数量' }} ÷ 实际净发主料计算；内外盒分别列出预计耗用，不与主料混加。</p>
        </fieldset>
        <label v-else class="field"><span>原计划产量（{{ outputUnit }}）</span><input class="input" type="number" min="1" v-model.number="outputQuantity" /></label>
        <p class="hint">最后一箱不足整箱时，在回仓记录中填写尾箱实际盒数，例如 1 尾箱 × 3 盒；未装箱的数量仍登记为散装。</p>
        <p v-if="packagingLocked" class="hint">已有回仓记录，成品、主料和包装换算保留原设置；新包装请另建任务。</p>
      </div>
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
.output-choice{display:flex;align-items:center;gap:8px}.output-choice input{width:18px;height:18px}.output-plan{display:grid;gap:12px;padding:14px;background:#f3f7f8;border-radius:8px}.task-form,fieldset{display:grid;gap:16px}.task-form{margin-top:16px}fieldset{padding:0;border:0;min-width:0}.target-row{display:grid;grid-template-columns:minmax(0,2fr) minmax(0,1fr) auto;gap:12px;align-items:end;border-bottom:1px solid #e5ecee;padding-bottom:16px}.target-row>*{min-width:0}fieldset>.btn{justify-self:start}.form-actions{display:flex;gap:10px}.form-alert{color:#8f1d25}.hint{margin:0}@media(max-width:600px){.target-row{grid-template-columns:minmax(0,1fr)}.target-row>.btn{justify-self:start}}
</style>
