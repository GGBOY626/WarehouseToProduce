<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import ProductionTaskTargets from "../components/ProductionTaskTargets.vue";
import ProductionTaskOutput from "../components/ProductionTaskOutput.vue";
import MaterialBatchPicker from "../components/MaterialBatchPicker.vue";
import SearchPicker from "../components/SearchPicker.vue";
import { errorMessage } from "../api/http";
import { movementsApi, personsApi, productsApi, productionTasksApi } from "../api";
import type {
  Direction,
  ItemInput,
  MovementDraft,
  Product,
  ProductionTaskOption,
  ProductionTask,
} from "../types";
import { directionLabel } from "../utils/labels";
import { nowLocalInput, toInstant, toLocalInput } from "../utils/time";
import { createUuid } from "../utils/uuid";
import { calculateQuantity, quantityError, cartonCount } from "../utils/quantity";
import { availableCartonSpecs, initialCartonSpec } from "../utils/cartonSpecs";
import { taskCartonUnits, unitsForBoxes, boxesForUnits } from "../utils/taskPackaging";
import { preparePhoto } from "../utils/photo";
const route = useRoute(),
  router = useRouter();
const editId = route.params.id ? Number(route.params.id) : undefined;
const direction = ref<Direction>(
  route.params.direction === "production-to-warehouse"
    ? "PRODUCTION_TO_WAREHOUSE"
    : "WAREHOUSE_TO_PRODUCTION",
);
const blankItem = (): ItemInput => ({
  batchNo: "",
  materialBatch: "",
  subBatch: "",
  fullCartons: 0,
  tailCartons: 0,
  looseUnits: 0,
  quantityUnknown: false,
  remarks: "",
  issues: [],
  manualTotal: false,
});
const draft = ref<MovementDraft>({
  idempotencyKey: createUuid(),
  direction: direction.value,
  movementTime: nowLocalInput(),
  manufactureLot: "",
  returnMovement: false,
  productionTaskId: undefined,
  remarks: "",
  items: [blankItem()],
});
const files = ref<
  {
    file: File;
    url: string;
    status: "preparing" | "ready" | "uploading" | "uploaded" | "prepare-failed" | "upload-failed";
    progress: number;
  }[]
>([]);
const saving = ref(false),
  dirty = ref(false),
  message = ref("");
const savedMovementId = ref<number>();
const productionTasks = ref<ProductionTaskOption[]>([]);
const taskLoading = ref(false), taskError = ref("");
const initialized = ref(false);
const originalTask = ref<{ id: number; name: string; selection: string }>();
const taskSelection = computed(() => JSON.stringify([direction.value, draft.value.returnMovement,
  [...new Set(draft.value.items.map(i => JSON.stringify([i.productId,i.materialBatch || ""])))].sort()]));
const keepOriginalTask = computed(() => !!originalTask.value && originalTask.value.id === draft.value.productionTaskId && originalTask.value.selection === taskSelection.value);
let taskRequest = 0;
async function refreshTasks() {
  if (!initialized.value) return;
  const request = ++taskRequest;
  const ids = [...new Set(draft.value.items.map(i => i.productId))];
  productionTasks.value = []; taskError.value = "";
  if (!ids.length || ids.some(id => !id)) {
    taskLoading.value = false;
    if (!keepOriginalTask.value) draft.value.productionTaskId = undefined;
    return;
  }
  taskLoading.value = true;
  try {
    const options = await productionTasksApi.linkable(draft.value.items.map(i => ({productId:i.productId!,materialBatch:i.materialBatch})), direction.value, draft.value.returnMovement);
    if (request !== taskRequest) return;
    productionTasks.value = options;
    if (!keepOriginalTask.value && !options.some(t => t.id === draft.value.productionTaskId)) draft.value.productionTaskId = undefined;
  } catch (e) { if (request === taskRequest) taskError.value = errorMessage(e); }
  finally { if (request === taskRequest) taskLoading.value = false; }
}
watch(taskSelection, refreshTasks);
const selectedTask = ref<ProductionTask>();
const previewLoading = ref(false), previewError = ref("");
let previewRequest = 0;
async function loadTaskPreview() {
  const request = ++previewRequest, id = draft.value.productionTaskId;
  selectedTask.value = undefined; previewError.value = "";
  previewLoading.value = !!id;
  if (!id) return;
  try {
    const task = await productionTasksApi.detail(id);
    if (request === previewRequest) {
      selectedTask.value = task;
      if (direction.value === 'PRODUCTION_TO_WAREHOUSE' && !draft.value.returnMovement && task.output?.boxesPerCarton && task.output.unitsPerBox) {
        for (const item of draft.value.items) if (!item.id && item.productId === task.output.productId) {
          item.unitsPerCarton = taskCartonUnits(task.output);
          quantityChanged(item);
        }
      }
    }
  } catch (e) { if (request === previewRequest) previewError.value = errorMessage(e); }
  finally { if (request === previewRequest) previewLoading.value = false; }
}
function taskPacking(item: ItemInput) {
  const output = selectedTask.value?.output;
  return direction.value === 'PRODUCTION_TO_WAREHOUSE' && !draft.value.returnMovement && output && output.productId === item.productId && output.boxesPerCarton && output.unitsPerBox ? output : undefined;
}
function tailBoxes(item: ItemInput) {
  const packing = taskPacking(item);
  return item.tailUnitsPerCarton == null ? undefined : packing ? boxesForUnits(packing, item.tailUnitsPerCarton) : item.tailUnitsPerCarton;
}
function setTailBoxes(item: ItemInput, event: Event) {
  const value = (event.target as HTMLInputElement).value;
  const packing = taskPacking(item);
  item.tailUnitsPerCarton = value === '' ? null : packing ? unitsForBoxes(packing, Number(value)) : Number(value);
  quantityChanged(item);
}
watch([() => draft.value.productionTaskId, taskSelection], loadTaskPreview);
const draftKey = computed(() => `movement-draft:${direction.value}`);
const productUsage = computed<Direction>(() => draft.value.returnMovement ? "WAREHOUSE_TO_PRODUCTION" : direction.value);
const needsMaterialBatch = computed(() => direction.value === "WAREHOUSE_TO_PRODUCTION" || draft.value.returnMovement);
function returnChanged() {
  draft.value.items = [blankItem()];
  draft.value.manufactureLot = "";
}
const calculate = calculateQuantity;
function commonSpecs(item: ItemInput) {
  return availableCartonSpecs(item.product);
}
function addSpecification(item: ItemInput) {
  const packing=taskPacking(item);
  draft.value.items.push({ ...blankItem(), productId: item.productId, product: item.product, batchNo: item.batchNo, materialBatch: item.materialBatch, subBatch: item.subBatch, unitsPerCarton: packing ? taskCartonUnits(packing) : initialCartonSpec(item.product) ?? null, quantityUnknown: item.quantityUnknown });
}
function displayedTotal(item: ItemInput) {
  if (item.quantityUnknown) return undefined;
  return item.manualTotal ? item.totalUnits : calculate(item);
}
function selectProduct(item: ItemInput, p?: Product) {
  item.id = undefined;
  item.subBatch = "";
  item.unitsPerCarton = initialCartonSpec(p) ?? null;
  item.product = p;
  item.productId = p?.id;
  item.batchNo = p?.materialCode || "";
  const batches = p?.materialBatches || (p?.materialBatch ? [p.materialBatch] : []);
  item.materialBatch = batches.length === 1 ? batches[0] : "";
  item.manualTotal = false;
  item.quantityUnknown = !!p?.quantityUnknown;

  item.totalUnits = calculate(item);
}
function quantityChanged(item: ItemInput) {
  if (!item.manualTotal) item.totalUnits = calculate(item);
}
function toggleUnknownQuantity(item: ItemInput) {
  if (item.quantityUnknown) {
    item.totalUnits = undefined;
    item.manualTotal = false;
  }
}
async function pickFiles(event: Event) {
  const input = event.target as HTMLInputElement;
  for (const file of Array.from(input.files || [])) {
    if (files.value.length >= 10) break;
    const entry = {
      file,
      url: URL.createObjectURL(file),
      status: "preparing" as "preparing" | "ready" | "uploading" | "uploaded" | "prepare-failed" | "upload-failed",
      progress: 0,
    };
    files.value.push(entry);
    try {
      entry.file = await preparePhoto(file);
      entry.status = "ready";
    } catch {
      entry.status = "prepare-failed";
      message.value = "照片处理失败，请删除后重新拍摄或选择 JPEG/PNG 图片。";
    }
  }
  input.value = "";
}
function removeFile(index: number) {
  if (!confirm("删除这张待上传照片？")) return;
  URL.revokeObjectURL(files.value[index].url);
  files.value.splice(index, 1);
}
function validate() {
  if (draft.value.productionTaskId && previewLoading.value) return "正在读取任务包装设置，请稍候。";
  if (draft.value.productionTaskId && previewError.value) return "读取任务设置失败，请重试后保存。";
  if (taskLoading.value) return "生产任务正在加载，请稍候。";
  if (taskError.value && draft.value.productionTaskId && !keepOriginalTask.value) return "请重试加载生产任务后保存。";
  if (!draft.value.senderPersonId || !draft.value.receiverPersonId)
    return "请选择发送人和接收人。";
  for (let i = 0; i < draft.value.items.length; i++) {
    const x = draft.value.items[i];
    const invalidQuantity = quantityError(x);
    if (invalidQuantity) return `产品明细 ${i + 1}：${invalidQuantity}`;
    if (!x.productId) return `请选择产品明细 ${i + 1} 的产品。`;
    if ((needsMaterialBatch.value || (x.product?.materialBatches?.length || 0) > 1) && !x.materialBatch?.trim()) return `请选择产品明细 ${i + 1} 的具体物料批次。`;
    if (!x.batchNo.trim()) return `请填写产品明细 ${i + 1} 的物料编码。`;
    if (
      !x.quantityUnknown &&
      x.fullCartons <= 0 &&
      x.tailCartons <= 0 &&
      x.looseUnits <= 0 &&
      (!x.totalUnits || x.totalUnits <= 0)
    )
      return `产品明细 ${i + 1} 至少填写一种数量。`;
  }
  return "";
}
function payload() {
  return {
    idempotencyKey: draft.value.idempotencyKey,
    direction: direction.value,
    movementTime: toInstant(draft.value.movementTime),
    senderPersonId: draft.value.senderPersonId,
    receiverPersonId: draft.value.receiverPersonId,

    returnMovement: direction.value === "PRODUCTION_TO_WAREHOUSE" && draft.value.returnMovement,
    productionTaskId: draft.value.productionTaskId,
    remarks: editId ? draft.value.remarks : undefined,
    items: draft.value.items.map((x) => ({
      id: x.id,
      productId: x.productId,
      unitsPerCarton: x.unitsPerCarton || null,
      tailCartons: Number(x.tailCartons) || 0,
      tailUnitsPerCarton: x.tailCartons > 0 ? Number(x.tailUnitsPerCarton) : null,
      batchNo: x.batchNo,
      materialBatch: x.materialBatch || "",
      subBatch: x.subBatch || "",
      fullCartons: Number(x.fullCartons) || 0,
      looseUnits: Number(x.looseUnits) || 0,
      totalUnits:
        x.quantityUnknown ? undefined : x.manualTotal && x.totalUnits !== undefined
          ? Number(x.totalUnits)
          : displayedTotal(x),
      quantityUnknown: x.quantityUnknown,
      remarks: x.remarks,
      issues: editId ? x.issues : [],
    })),
  };
}
async function save() {
  message.value = validate();
  if (message.value) {
    window.setTimeout(
      () => document.querySelector(".sticky-alert")?.scrollIntoView({ block: "nearest" }),
      0,
    );
    return;
  }
  if (files.value.some((entry) => entry.status === "preparing")) {
    message.value = "照片正在准备，请稍候再保存。";
    return;
  }
  if (files.value.some((entry) => entry.status === "prepare-failed")) {
    message.value = "存在处理失败的照片，请删除后重新选择。";
    return;
  }
  saving.value = true;
  try {
    const movementId = editId ?? savedMovementId.value;
    const result = movementId
      ? await movementsApi.update(movementId, payload())
      : await movementsApi.create(payload());
    savedMovementId.value = result.id;
    result.items.forEach((saved, index) => { draft.value.items[index].id = saved.id; });
    for (const entry of files.value) {
      if (entry.status === "uploaded") continue;
      entry.status = "uploading";
      entry.progress = 0;
      try {
        await movementsApi.uploadPhoto(result.id, entry.file, (progress) => entry.progress = progress);
        entry.status = "uploaded";
      } catch (error) {
        entry.status = "upload-failed";
        throw error;
      }
    }
    localStorage.setItem(`last-persons:${direction.value}`, JSON.stringify({
      sender: result.senderPersonId,
      receiver: result.receiverPersonId,
    }));
    await nextTick();
    localStorage.removeItem(draftKey.value);
    dirty.value = false;
    await router.replace(`/movements/${result.id}`);
  } catch (e) {
    message.value = errorMessage(e);
  } finally {
    saving.value = false;
  }
}
function beforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value) {
    e.preventDefault();
    e.returnValue = "";
  }
}
onBeforeRouteLeave(
  () => !dirty.value || confirm("表单还有未保存内容，确定离开？"),
);
watch(
  draft,
  () => {
    dirty.value = true;
    if (!editId)
      localStorage.setItem(draftKey.value, JSON.stringify(draft.value));
  },
  { deep: true },
);
onMounted(async () => {
  window.addEventListener("beforeunload", beforeUnload);
  if (editId) {
    const m = await movementsApi.detail(editId);
    const catalog = await productsApi.search('', true);
    direction.value = m.direction;
    draft.value = {
      idempotencyKey: createUuid(),
      direction: m.direction,
      movementTime: toLocalInput(m.movementTime),
      senderPersonId: m.senderPersonId,
      receiverPersonId: m.receiverPersonId,
      manufactureLot: m.manufactureLot || "",
      returnMovement: m.returnMovement,
      productionTaskId: m.productionTaskId,
      remarks: m.remarks || "",
      items: m.items.map((x) => ({
        id: x.id,
        unitsPerCarton: x.unitsPerCarton ?? null,
        tailCartons: x.tailCartons || 0,
        tailUnitsPerCarton: x.tailUnitsPerCarton,
        productId: x.productId,
        product: {
          id: x.productId,
          name: x.productName,
          materialCode: x.batchNo,
          defaultUnitsPerCarton: x.unitsPerCarton,
          materialBatches: catalog.find(p => p.id === x.productId)?.materialBatches,
          cartonSpecs: catalog.find(p => p.id === x.productId)?.cartonSpecs,
          baseUnit: x.baseUnit,
          quantityUnknown: false,
          movementDirection: m.returnMovement ? "WAREHOUSE_TO_PRODUCTION" : m.direction,
          active: true,
        },
        batchNo: x.batchNo,
        materialBatch: x.materialBatch ?? m.manufactureLot ?? "",
        subBatch: x.subBatch || "",
        fullCartons: x.fullCartons,
        looseUnits: x.looseUnits,
        totalUnits: x.totalUnits,
        quantityUnknown: x.quantityUnknown,
        manualTotal: x.totalUnitsOverridden,
        remarks: x.remarks || "",
        issues: x.issues.map((y) => ({
          type: y.type,
          description: y.description,
        })),
      })),
    };
    if (m.productionTaskId) originalTask.value = { id: m.productionTaskId, name: m.productionTaskName || '原关联任务', selection: taskSelection.value };
    dirty.value = false;
  } else {
    const cached = localStorage.getItem(draftKey.value);
    if (cached && confirm("发现未保存的表单草稿，是否继续填写？"))
      draft.value = { ...JSON.parse(cached), returnMovement: !!JSON.parse(cached).returnMovement };
    else {
      const last = localStorage.getItem(`last-persons:${direction.value}`);
      if (last) {
        const p = JSON.parse(last);
        draft.value.senderPersonId = p.sender;
        draft.value.receiverPersonId = p.receiver;
      }
      dirty.value = false;
    }
  }
  draft.value.items.forEach(item => {
    item.tailCartons ??= 0;
  });
  if (!editId && draft.value.items.some(i => i.productId)) {
    const catalog = await productsApi.search('', true, productUsage.value);
    for (const item of draft.value.items) {
      const current = catalog.find(p => p.id === item.productId);
      if (current && item.product) {
        item.product.materialBatches = current.materialBatches;
        item.product.cartonSpecs = current.cartonSpecs;
        item.product.defaultUnitsPerCarton = current.defaultUnitsPerCarton;
      }
    }
  }
  for (const item of draft.value.items) {
    if (!('unitsPerCarton' in item)) item.unitsPerCarton = initialCartonSpec(item.product) ?? null;
    if (item.materialBatch === undefined) item.materialBatch = draft.value.manufactureLot || "";
  }
  initialized.value = true;
  await refreshTasks();
  await nextTick();
  dirty.value = false;
});
onBeforeUnmount(() => {
  previewRequest++; taskRequest++;
  window.removeEventListener("beforeunload", beforeUnload);
  files.value.forEach((x) => URL.revokeObjectURL(x.url));
});
</script>
<template>
  <div class="page">
    <div
      :class="[
        'direction-strip',
        direction === 'PRODUCTION_TO_WAREHOUSE' && 'return',
      ]"
    >
      {{ directionLabel(direction) }}
    </div>
    <h1 class="page-title">{{ editId ? "编辑流转记录" : "登记物料流转" }}</h1>
    <p class="page-lead">带 * 的内容必须填写。没有照片也可以保存。</p>
    <div v-if="message" class="form-alert" role="alert">{{ message }}</div>
    <section class="card card-pad stack">
      <label v-if="direction === 'PRODUCTION_TO_WAREHOUSE'" class="return-choice">
        <input type="checkbox" v-model="draft.returnMovement" @change="returnChanged" />
        <span><strong>这是退回物料</strong><small>仓库之前送入生产车间，现在将多余物料退回仓库</small></span>
      </label>
      <div class="field">
        <label class="required">实际流转时间</label
        ><input
          class="input"
          type="datetime-local"
          v-model="draft.movementTime"
        />
      </div>
      <div class="grid-2 people">
        <SearchPicker
          v-model="draft.senderPersonId"
          label="发送人"
          placeholder="输入姓名搜索"
          :load="(q) => personsApi.search(q)"
        /><SearchPicker
          v-model="draft.receiverPersonId"
          label="接收人"
          placeholder="输入姓名搜索"
          :load="(q) => personsApi.search(q)"
        />
      </div>
    </section>
    <section class="section">
      <div class="section-head">
        <h2 class="section-title">产品明细</h2>
        <span class="hint">{{ draft.items.length }} 项</span>
      </div>
      <article
        v-for="(item, index) in draft.items"
        :key="index"
        class="item-card card card-pad"
      >
        <div class="item-title">
          <strong>产品明细 {{ index + 1 }}</strong
          ><button
            v-if="draft.items.length > 1"
            type="button"
            class="text-danger"
            @click="draft.items.splice(index, 1)"
          >
            移除
          </button>
        </div>
        <div class="stack">
          <SearchPicker
            v-model="item.productId"
            label="产品"
            placeholder="输入产品名、物料编码或物料批次"
            :load="(q) => productsApi.search(q, false, productUsage)"
            @select="(p) => selectProduct(item, p)"
          />
          <div class="field">
            <label class="required">物料编码</label
            ><input
              class="input mono"
              v-model.trim="item.batchNo"
              placeholder="选择产品后自动带入"
            />
          </div>
          <MaterialBatchPicker v-model="item.materialBatch" :batches="item.product?.materialBatches" :required="needsMaterialBatch" />
          <div class="field">
            <label :for="`sub-batch-${index}`">区分批次（可选）</label>
            <input :id="`sub-batch-${index}`" class="input mono" maxlength="100" v-model.trim="item.subBatch" placeholder="外盒同编码、同批次仍需区分时填写" />
            <small class="hint">填写实际区分标识。不同区分批次分别统计；不需要区分时留空。</small>
          </div>
          <div class="packing-fields">
            <p v-if="taskPacking(item)" class="hint">任务包装：{{ taskPacking(item)!.boxesPerCarton }} 盒/箱 × {{ taskPacking(item)!.unitsPerBox }} {{ taskPacking(item)!.primaryMaterialUnit }}/盒。尾箱可填写实际盒数。</p>
            <div class="grid-2">
              <div class="field"><label :for="`size-${index}`" :class="{required:item.fullCartons > 0 && commonSpecs(item).length > 1}">完整箱每箱数量</label><input :id="`size-${index}`" class="input mono" type="number" min="1" step="1" inputmode="numeric" v-model.number="item.unitsPerCarton" :readonly="!!taskPacking(item)" @input="quantityChanged(item)" placeholder="选择下方规格或输入每箱数量" /><small v-if="!taskPacking(item) && commonSpecs(item).length > 1" class="hint">有多种每箱数量，请选择本次完整箱规格；仅尾箱或散装可留空。</small></div>
              <div class="field"><label :for="`full-${index}`">完整箱数</label><input :id="`full-${index}`" class="input mono" type="number" min="0" step="1" inputmode="numeric" v-model.number="item.fullCartons" @input="quantityChanged(item)" /></div>
            </div>
            <div v-if="!taskPacking(item) && commonSpecs(item).length" class="spec-options" aria-label="选择每箱数量">
              <small class="hint">预设规格</small><button v-for="size in commonSpecs(item)" :key="size" type="button" :class="{active:item.unitsPerCarton===size}" :aria-pressed="item.unitsPerCarton===size" @click="item.unitsPerCarton=size;quantityChanged(item)">{{size}} {{item.product?.baseUnit || '个'}}/箱</button>
            </div>
            <details :open="item.tailCartons>0 || item.looseUnits>0" class="optional-packing">
              <summary>尾箱 / 真正散装（可选）</summary>
              <div class="grid-2">
                <div class="field"><label :for="`tail-${index}`">尾箱箱数</label><input :id="`tail-${index}`" class="input mono" type="number" min="0" step="1" inputmode="numeric" v-model.number="item.tailCartons" @input="quantityChanged(item)" /></div>
                <div v-if="taskPacking(item)" class="field"><label :for="`tail-boxes-${index}`">每个尾箱实际盒数</label><input :id="`tail-boxes-${index}`" class="input mono" type="number" min="1" step="1" :value="tailBoxes(item)" @input="setTailBoxes(item, $event)" placeholder="例如 3 盒" /><small class="hint">实际 {{ item.tailUnitsPerCarton || 0 }} {{ item.product?.baseUnit }} / 尾箱</small></div>
                <div v-else class="field"><label :for="`tail-size-${index}`">每个尾箱实际数量</label><input :id="`tail-size-${index}`" class="input mono" type="number" min="1" step="1" inputmode="numeric" v-model.number="item.tailUnitsPerCarton" @input="quantityChanged(item)" placeholder="例如 3" /></div>
              </div>
              <small class="hint">尾箱计入总箱数。尾箱数量不同时，点击“添加同物料规格 / 批次”分开填写。</small>
              <div class="field"><label :for="`loose-${index}`">真正散装（未装箱的{{item.product?.baseUnit || '数量'}}）</label><input :id="`loose-${index}`" class="input mono" type="number" min="0" step="1" inputmode="numeric" v-model.number="item.looseUnits" @input="quantityChanged(item)" /></div>
            </details>
            <button v-if="item.productId" class="btn btn-ghost" type="button" @click="addSpecification(item)">＋ 添加同物料规格 / 批次</button>
          </div>
          <label class="unknown-quantity">
            <input type="checkbox" v-model="item.quantityUnknown" :disabled="item.product?.quantityUnknown" @change="toggleUnknownQuantity(item)" />
            {{item.product?.quantityUnknown ? '该产品已设置为数量不确定' : '数量不确定（无法清点或没有数量标识）'}}
          </label>
          <div class="total-box">
            <span>总箱数：{{ cartonCount(item) }} 箱（{{ item.fullCartons || 0 }} 满箱 + {{ item.tailCartons || 0 }} 尾箱）</span>
            <span>总数量</span
            ><strong class="mono"
              >{{ displayedTotal(item) ?? "未知"
              }}<small v-if="displayedTotal(item) != null">
                {{ item.product?.baseUnit || "个" }}</small
              ></strong
            ><label class="manual"
              ><input type="checkbox" v-model="item.manualTotal" :disabled="item.quantityUnknown" />
              人工填写总数量</label
            ><input
              v-if="item.manualTotal"
              class="input mono"
              type="number"
              inputmode="numeric"
              min="0"
              v-model.number="item.totalUnits"
            /><small
              v-if="item.manualTotal && calculate(item) != null"
              class="hint"
              >按箱规应为 {{ calculate(item) }}，当前填写
              {{ item.totalUnits ?? "—" }}</small
            ><small
              v-else-if="
                item.product &&
                !item.unitsPerCarton &&
                item.fullCartons > 0
              "
              class="hint"
              >此条记录未填写箱规，总数量未知。</small
            >
          </div>
          <div class="field">
            <label>产品备注</label
            ><textarea
              class="textarea"
              v-model.trim="item.remarks"
              maxlength="1000"
              placeholder="填写这个产品的补充情况或异常说明"
            ></textarea>
          </div>
        </div>
      </article>
      <button
        class="btn btn-ghost btn-block add-item"
        type="button"
        @click="draft.items.push(blankItem())"
      >
        ＋ 添加产品
      </button>
    </section>
    <section class="section card card-pad stack">
      <div>
        <div class="field-label">
          照片 <span class="hint">最多 10 张，可保存后补拍</span>
        </div>
        <div class="photo-actions">
          <label class="btn btn-secondary"
            >拍照<input
              class="sr-only"
              type="file"
              accept="image/*"
              capture="environment"
              @change="pickFiles" /></label
          ><label class="btn btn-ghost"
            >从相册选择<input
              class="sr-only"
              type="file"
              accept="image/jpeg,image/png,image/webp,image/heic"
              multiple
              @change="pickFiles"
          /></label>
        </div>
        <div v-if="files.length" class="photo-grid">
          <div v-for="(entry, i) in files" :key="entry.url" class="photo">
            <img :src="entry.url" alt="待上传照片" /><button
              type="button"
              @click="removeFile(i)"
              aria-label="删除照片"
            >
              ×</button
            ><span v-if="entry.status === 'preparing'">正在优化…</span
            ><span v-else-if="entry.status === 'ready'">已准备</span
            ><span v-else-if="entry.status === 'uploading'">上传 {{ entry.progress }}%</span
            ><span v-else-if="entry.status === 'uploaded'">上传完成</span
            ><span v-else class="failed">{{ entry.status === 'prepare-failed' ? '处理失败' : '上传失败' }}</span>
          </div>
        </div>
      </div>
    </section>
    <section class="section card card-pad stack">
      <div class="field">
        <label for="production-task">关联生产任务（可选）</label>
        <select id="production-task" class="select" v-model="draft.productionTaskId" :disabled="taskLoading">
          <option :value="undefined">不关联生产任务</option>
          <option v-if="keepOriginalTask && !productionTasks.some(t => t.id === originalTask?.id)" :value="originalTask!.id">{{ originalTask!.name }}</option>
          <option v-for="task in productionTasks" :key="task.id" :value="task.id">{{ task.name }}</option>
        </select>
        <small v-if="taskLoading" class="hint">正在查找匹配任务…</small>
        <small v-else-if="draft.items.some(i => !i.productId)" class="hint">先选择产品，再显示匹配的生产任务。</small>
        <small v-else-if="!productionTasks.length && !keepOriginalTask" class="hint">没有匹配任务。{{ direction === 'WAREHOUSE_TO_PRODUCTION' ? '仅显示需要本单全部物料且进行中的任务。' : draft.returnMovement ? '仅显示需要本单物料且未取消的任务。' : '请先在任务中设置对应成品；进行中即可分批回仓。' }}</small>
        <small v-else class="hint">{{ direction === 'WAREHOUSE_TO_PRODUCTION' ? '只显示需要本单全部物料且进行中的任务。' : draft.returnMovement ? '退料计入任务的净发料，不计入成品产量。' : '按任务对应成品累计回仓产量；进行中和已完成任务均可补登记。' }}</small>
        <p v-if="taskError" class="form-alert" role="alert">{{ taskError }} <button type="button" class="btn btn-ghost" @click="refreshTasks">重试</button></p>
        <div v-if="draft.productionTaskId" class="task-preview" aria-live="polite" :aria-busy="previewLoading">
          <p v-if="previewLoading" class="hint">正在读取任务进度和缺料情况…</p>
          <p v-else-if="previewError" class="form-alert" role="alert">{{ previewError }} <button type="button" class="btn btn-ghost" @click="loadTaskPreview">重试</button></p>
          <template v-else-if="selectedTask">
            <div class="preview-heading"><strong>{{ selectedTask.name }}</strong><b>备料进度 {{ selectedTask.progressPercent }}%</b></div>
            <small class="hint">{{ selectedTask.batchNo }} · {{ selectedTask.status === 'PREPARING' ? '进行中' : selectedTask.status === 'COMPLETED' ? '已完成' : selectedTask.status === 'CANCELLED' ? '已取消' : '进行中' }}</small>
            <ProductionTaskOutput v-if="selectedTask.output" :output="selectedTask.output" />
            <ProductionTaskTargets :targets="selectedTask.targets" />
            <small class="hint">{{ editId ? '按已保存记录统计，包含本单原数量；本次修改保存后更新。' : '按已保存记录统计，不含本次尚未保存的数量。' }}</small>
          </template>
        </div>
      </div>
    </section>
    <div class="sticky-actions">
      <div v-if="message" class="sticky-alert" role="alert">
        {{ message }}
      </div>
      <button
        class="btn btn-primary btn-block"
        :disabled="saving"
        @click="save"
      >
        {{ saving ? "正在保存…" : editId ? "保存修改" : "保存记录" }}
      </button>
    </div>
  </div>
</template>
<style scoped>
.packing-fields > .grid-2 { align-items: start; }
.task-preview{display:grid;gap:14px;margin-top:10px;padding-top:16px;border-top:1px solid #e5ecee;min-width:0}.preview-heading{display:flex;justify-content:space-between;gap:8px;flex-wrap:wrap}.preview-heading strong{overflow-wrap:anywhere}.preview-heading b{color:#267566;font-size:14px}

.packing-fields{display:grid;gap:12px}.spec-options{display:flex;flex-wrap:wrap;gap:6px;align-items:center}.spec-options button{border:1px solid var(--line);background:#fff;border-radius:6px;padding:7px 10px;min-height:36px;font-size:12px}.spec-options button.active{background:var(--soft-blue);border-color:var(--blue);color:var(--blue)}
.optional-packing{border:1px solid var(--line);border-radius:9px;padding:12px}.optional-packing summary{cursor:pointer;color:var(--blue);font-weight:650}.optional-packing[open] summary{margin-bottom:12px}.optional-packing>.hint{display:block;margin:8px 0 12px}

.form-alert {
  background: #fdeced;
  color: #8f1d25;
  border: 1px solid #f2c7ca;
  padding: 12px;
  border-radius: 9px;
  margin-bottom: 14px;
}
.sticky-alert {
  background: #fdeced;
  color: #8f1d25;
  border: 1px solid #f2c7ca;
  border-radius: 8px;
  padding: 9px 11px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 680;
}
.item-card {
  margin-bottom: 12px;
  border-top: 4px solid #174a68;
}
.item-title {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}
.text-danger {
  border: 0;
  background: none;
  color: #b4232c;
  min-height: 38px;
}
.total-box {
  background: #f3f7f8;
  border-radius: 9px;
  padding: 12px;
  display: grid;
  gap: 8px;
}
.total-box strong {
  font-size: 24px;
  color: #174a68;
}
.total-box small {
  font-size: 12px;
}
.manual {
  font-size: 13px;
  display: flex;
  gap: 8px;
  align-items: center;
  min-height: 34px;
}
.manual input,
.unknown-quantity input {
  width: 18px;
  height: 18px;
  accent-color: #174a68;
}
.unknown-quantity { display: flex; align-items: center; gap: 8px; min-height: 40px; font-size: 14px; font-weight: 680; color: #174a68; }
.return-choice{display:flex;gap:10px;align-items:flex-start;padding:12px;border:1px solid #e3b56d;background:#fff8e8;border-radius:9px;color:#744a10}.return-choice input{width:20px;height:20px;accent-color:#b75b18}.return-choice span{display:grid;gap:3px}.return-choice small{font-weight:400;color:#806239}
.add-item {
  border-style: dashed;
}
.photo-actions {
  display: flex;
  gap: 9px;
  margin-top: 9px;
}
.photo-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-top: 12px;
}
.photo {
  aspect-ratio: 1;
  position: relative;
  border-radius: 8px;
  overflow: hidden;
  background: #edf0f2;
}
.photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.photo button {
  position: absolute;
  right: 4px;
  top: 4px;
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.65);
  color: #fff;
  font-size: 20px;
}
.photo span {
  position: absolute;
  left: 4px;
  bottom: 4px;
  background: rgba(0, 0, 0, 0.7);
  color: #fff;
  padding: 3px 5px;
  border-radius: 4px;
  font-size: 11px;
}
.photo .failed {
  background: #b4232c;
}
@media (max-width: 390px) {
  .people {
    grid-template-columns: 1fr;
  }
  .photo-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
}
</style>
