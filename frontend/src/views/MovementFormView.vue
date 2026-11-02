<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import MaterialBatchPicker from "../components/MaterialBatchPicker.vue";
import ProductionNumberPicker from "../components/ProductionNumberPicker.vue";
import SearchPicker from "../components/SearchPicker.vue";
import { errorMessage } from "../api/http";
import { movementsApi, personsApi, productsApi, productionTasksApi } from "../api";
import type {
  Direction,
  ItemInput,
  MovementDraft,
  Product,
  BoxPacking,
  ProductionTaskOption,
} from "../types";
import { directionLabel } from "../utils/labels";
import { nowLocalInput, toInstant, toLocalInput } from "../utils/time";
import { createUuid } from "../utils/uuid";
import { calculateQuantity, quantityError, cartonCount } from "../utils/quantity";
import { availableCartonSpecs, initialCartonSpec } from "../utils/cartonSpecs";
import { initialBoxPacking, applyBoxPacking, syncBoxQuantity } from "../utils/boxPacking";
import { taskCartonUnits, unitsForBoxes, boxesForUnits, fullCartonsForPalletSelection, tailCartonsOnOpen } from "../utils/taskPackaging";
import { movementProductChoices } from "../utils/taskProductChoices";
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
  sku: "",
  materialBatch: "",
  productionNumber: "",
  subBatch: "",
  fullCartons: 0,
  wholePallets: 0,
  tailCountTouched: false,
  tailSectionOpen: false,
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
const palletSetupPromptIndex = ref<number | null>(null);
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
const selectedTask = computed(() => productionTasks.value.find(task => task.id === draft.value.productionTaskId));
const isFinishedReceipt = computed(() => direction.value === 'PRODUCTION_TO_WAREHOUSE' && !draft.value.returnMovement);
const isNewFinishedReceipt = computed(() => !editId && direction.value === 'PRODUCTION_TO_WAREHOUSE' && !draft.value.returnMovement);
function taskOutput(item: ItemInput) {
  const output = selectedTask.value;
  return isFinishedReceipt.value && output?.outputProductId === item.productId ? output : undefined;
}
function taskPacking(item: ItemInput) {
  const output = taskOutput(item);
  if (!output?.outputBoxesPerCarton || !output.outputUnitsPerBox || !output.outputQuantityBasis) return undefined;
  return { boxesPerCarton: output.outputBoxesPerCarton, unitsPerBox: output.outputUnitsPerBox, quantityBasis: output.outputQuantityBasis, primaryMaterialUnit: output.outputPrimaryMaterialUnit };
}
function palletCartonCount(item: ItemInput) {
  const taskCount = taskOutput(item)?.outputCartonsPerPallet;
  if (Number.isSafeInteger(taskCount) && Number(taskCount) > 0) return Number(taskCount);
  const packings = item.product?.boxPackings || [];
  const matchingPacking = packings.find(packing => packing.boxesPerCarton === item.boxesPerCarton && packing.unitsPerBox === item.unitsPerBox);
  const hasSelectedPacking = item.boxesPerCarton != null || item.unitsPerBox != null;
  const packing = matchingPacking || (!hasSelectedPacking && packings.length === 1 ? packings[0] : undefined);
  const count = packing?.cartonsPerPallet;
  return Number.isSafeInteger(count) && Number(count) > 0 ? Number(count) : undefined;
}
function wholePalletCartons(item: ItemInput) {
  return Number(item.wholePallets || 0) * (palletCartonCount(item) || 0);
}
function quantityItem(item: ItemInput): ItemInput {
  return {
    ...item,
    fullCartons: fullCartonsForPalletSelection(Number(item.fullCartons || 0), palletCartonCount(item), isNewFinishedReceipt.value ? Number(item.wholePallets || 0) : 0),
    looseUnits: isNewFinishedReceipt.value ? 0 : Number(item.looseUnits || 0),
    quantityUnknown: isNewFinishedReceipt.value ? false : item.quantityUnknown,
    manualTotal: item.manualTotal,
  };
}
function changeWholePallets(item: ItemInput, amount: number) {
  item.wholePallets = Math.max(0, Number(item.wholePallets || 0) + amount);
  quantityChanged(item);
}
function setWholePallets(item: ItemInput, event: Event) {
  const value = (event.target as HTMLInputElement).value;
  item.wholePallets = value === '' ? 0 : Math.max(0, Math.floor(Number(value) || 0));
  quantityChanged(item);
}
function toggleWholePallet(item: ItemInput, event: Event, index: number) {
  const checked = (event.target as HTMLInputElement).checked;
  if (!checked) {
    item.wholePallets = 0;
    palletSetupPromptIndex.value = null;
    quantityChanged(item);
    return;
  }
  if (!palletCartonCount(item)) {
    (event.target as HTMLInputElement).checked = false;
    palletSetupPromptIndex.value = index;
    return;
  }
  palletSetupPromptIndex.value = null;
  item.wholePallets = Math.max(1, Number(item.wholePallets || 0));
  item.fullCartons = 0;
  quantityChanged(item);
}
function openProductManagement() {
  window.open(router.resolve('/products').href, '_blank', 'noopener,noreferrer');
}
function changePalletNo(event: Event) {
  const value = (event.target as HTMLInputElement).value;
  draft.value.palletNo = value === '' ? undefined : Number(value);
}
function tailSectionToggled(item: ItemInput, event: Event) {
  const section = event.currentTarget as HTMLDetailsElement;
  item.tailSectionOpen = section.open;
  if (!section.open) return;
  const count = tailCartonsOnOpen(item.tailCartons, item.tailCountTouched === true, isNewFinishedReceipt.value);
  if (count !== item.tailCartons) {
    item.tailCartons = count;
    item.tailCountTouched = true;
    quantityChanged(item);
  }
}
function changeTailCartons(item: ItemInput, difference: number) {
  item.tailCountTouched = true;
  item.tailCartons = Math.max(0, Number(item.tailCartons || 0) + difference);
  quantityChanged(item);
}
function tailCartonsChanged(item: ItemInput) {
  item.tailCountTouched = true;
  quantityChanged(item);
}
function tailBoxes(item: ItemInput) {
  const packing = taskPacking(item);
  if(!packing && item.unitsPerBox!=null)return item.tailBoxesPerCarton;
  return item.tailUnitsPerCarton == null ? undefined : packing ? boxesForUnits(packing, item.tailUnitsPerCarton) : item.tailUnitsPerCarton;
}
function setTailBoxes(item: ItemInput, event: Event) {
  const value = (event.target as HTMLInputElement).value;
  const packing = taskPacking(item);
  if(!packing && item.unitsPerBox!=null){item.tailBoxesPerCarton=value===''?null:Number(value);quantityChanged(item);return;}
  item.tailUnitsPerCarton = value === '' ? null : packing ? unitsForBoxes(packing, Number(value)) : Number(value);
  quantityChanged(item);
}
watch(selectedTask, task => {
  if (!task || !isNewFinishedReceipt.value) return;
  for (const item of draft.value.items) if (!item.id && item.productId === task.outputProductId && task.outputCartonUnits) {
    item.unitsPerCarton = task.outputCartonUnits;
    quantityChanged(item);
  }
});
const draftKey = computed(() => `movement-draft:${direction.value}`);
const productUsage = computed<Direction>(() => draft.value.returnMovement ? "WAREHOUSE_TO_PRODUCTION" : direction.value);
const needsMaterialBatch = computed(() => direction.value === "WAREHOUSE_TO_PRODUCTION" || draft.value.returnMovement);
const palletCount = computed(() => {
  if (!isNewFinishedReceipt.value) return Number(draft.value.palletCount || 1);
  const count = draft.value.items.reduce((total, item) => {
    const fullPallets = Math.max(0, Number(item.wholePallets || 0));
    const remainder = Math.max(0, Number(item.fullCartons || 0)) + Math.max(0, Number(item.tailCartons || 0));
    if (!remainder) return total + fullPallets;
    const cartonsPerPallet = palletCartonCount(item);
    return total + fullPallets + (cartonsPerPallet ? Math.ceil(remainder / cartonsPerPallet) : 1);
  }, 0);
  return Math.max(1, count);
});
const nextPalletNo = computed(() => draft.value.palletNo || 1);
const palletRangeText = computed(() => {
  const start = Number(draft.value.palletNo || nextPalletNo.value);
  const end = start + Math.max(1, palletCount.value) - 1;
  return start === end ? String(start) : `${start}-${end}`;
});
async function loadProductChoices(query: string, item: ItemInput): Promise<Product[]> {
  const search = query.trim();
  if (search) {
    const catalog = await productsApi.search(search, false, productUsage.value);
    return movementProductChoices(catalog, item.productId);
  }
  const [catalog, taskProductIds] = await Promise.all([
    productsApi.search('', false, productUsage.value),
    productionTasksApi.productChoices(productUsage.value),
  ]);
  return movementProductChoices(catalog, item.productId, taskProductIds);
}
function returnChanged() {
  draft.value.items = [blankItem()];
  draft.value.manufactureLot = "";
}
const calculate = calculateQuantity;
function commonSpecs(item: ItemInput) {
  return availableCartonSpecs(item.product);
}
function boxed(item: ItemInput) {return !taskPacking(item)&&(item.boxesPerCarton!=null||item.unitsPerBox!=null)}
function choosePacking(item: ItemInput, packing: BoxPacking){applyBoxPacking(item,packing);quantityChanged(item)}
function chooseLegacy(item: ItemInput,size:number){applyBoxPacking(item);item.unitsPerCarton=size;quantityChanged(item)}
function legacySpecs(item: ItemInput){return commonSpecs(item).filter(n=>!item.product?.boxPackings?.some(p=>p.boxesPerCarton*p.unitsPerBox===n))}
function addSpecification(item: ItemInput) {
  const packing=taskPacking(item);
  draft.value.items.push({ ...blankItem(), productId: item.productId, product: item.product, batchNo: item.batchNo, sku: item.sku, materialBatch: item.materialBatch, subBatch: item.subBatch, unitsPerCarton: packing ? taskCartonUnits(packing) : initialCartonSpec(item.product) ?? null, quantityUnknown: item.quantityUnknown });
  const added=draft.value.items[draft.value.items.length-1]!;
  if(!packing)applyBoxPacking(added,initialBoxPacking(item.product));
}
function displayedTotal(item: ItemInput) {
  const quantity = quantityItem(item);
  if (quantity.quantityUnknown) return undefined;
  return quantity.manualTotal ? quantity.totalUnits : calculate(quantity);
}
function selectProduct(item: ItemInput, p?: Product) {
  item.id = undefined;
  item.subBatch = "";
  palletSetupPromptIndex.value = null;
  item.unitsPerCarton = initialCartonSpec(p) ?? null;
  item.product = p;
  item.productId = p?.id;
  item.wholePallets = 0;
  item.tailCountTouched = false;
  item.tailSectionOpen = false;
  applyBoxPacking(item,initialBoxPacking(p));
  item.batchNo = p?.materialCode || "";
  item.sku = p?.sku || "";
  const batches = p?.materialBatches || (p?.materialBatch ? [p.materialBatch] : []);
  item.materialBatch = batches.length === 1 ? batches[0] : "";
  const numbers = p?.productionNumbers || [];
  item.productionNumber = numbers.length === 1 ? numbers[0] : "";
  item.manualTotal = false;
  item.quantityUnknown = isNewFinishedReceipt.value ? false : !!p?.quantityUnknown;

  quantityChanged(item);
}
function quantityChanged(item: ItemInput) {
  if (isNewFinishedReceipt.value && Number(item.wholePallets || 0) > 0 && !palletCartonCount(item)) {
    item.wholePallets = 0;
    palletSetupPromptIndex.value = draft.value.items.indexOf(item);
  }
  if(!taskPacking(item))syncBoxQuantity(item);
  else if (!Number(item.tailCartons || 0)) {
    item.tailBoxesPerCarton = null;
    item.tailUnitsPerCarton = null;
  }
  const quantity = quantityItem(item);
  if (!quantity.manualTotal) item.totalUnits = calculate(quantity);
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
  if (taskLoading.value) return "生产任务正在加载，请稍候。";
  if (taskError.value && draft.value.productionTaskId && !keepOriginalTask.value) return "请重试加载生产任务后保存。";
  if (isFinishedReceipt.value && draft.value.palletNo != null && (!Number.isInteger(draft.value.palletNo) || draft.value.palletNo < 1)) return "PALLET NO 必须是大于零的整数。";
  if (editId && (!draft.value.senderPersonId || !draft.value.receiverPersonId))
    return "请选择发送人和接收人。";
  for (let i = 0; i < draft.value.items.length; i++) {
    const x = draft.value.items[i];
    const quantity = quantityItem(x);
    const invalidQuantity = quantityError(quantity);
    if (invalidQuantity) return `产品明细 ${i + 1}：${invalidQuantity}`;
    if (!x.productId) return `请选择产品明细 ${i + 1} 的产品。`;
    if (!isNewFinishedReceipt.value && (needsMaterialBatch.value || (x.product?.materialBatches?.length || 0) > 1) && !x.materialBatch?.trim()) return `请选择产品明细 ${i + 1} 的具体物料批次。`;
    if ((x.product?.productionNumbers?.length || 0) > 1 && !x.productionNumber?.trim() && !x.id) return `请选择产品明细 ${i + 1} 的具体生产编号。`;
    if (!x.batchNo.trim()) return `请填写产品明细 ${i + 1} 的物料编码。`;
    if (
      !quantity.quantityUnknown &&
      quantity.fullCartons <= 0 &&
      quantity.tailCartons <= 0 &&
      quantity.looseUnits <= 0 &&
      (!quantity.totalUnits || quantity.totalUnits <= 0)
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
    senderPersonId: editId ? draft.value.senderPersonId : undefined,
    receiverPersonId: editId ? draft.value.receiverPersonId : undefined,

    returnMovement: direction.value === "PRODUCTION_TO_WAREHOUSE" && draft.value.returnMovement,
    productionTaskId: draft.value.productionTaskId,
    palletNo: isFinishedReceipt.value ? draft.value.palletNo : undefined,
    palletCount: isFinishedReceipt.value && draft.value.palletNo != null ? palletCount.value : undefined,
    remarks: editId ? draft.value.remarks : undefined,
    items: draft.value.items.map((x) => {
      const quantity = quantityItem(x);
      return {
        id: x.id,
        productId: x.productId,
        boxesPerCarton: taskPacking(x)?.boxesPerCarton ?? x.boxesPerCarton,
        unitsPerBox: taskPacking(x) ? (taskPacking(x)!.quantityBasis === 'BOX' ? 1 : taskPacking(x)!.unitsPerBox) : x.unitsPerBox,
        tailBoxesPerCarton: taskPacking(x)&&x.tailCartons>0?tailBoxes(x):!taskPacking(x)&&boxed(x)&&x.tailCartons>0?x.tailBoxesPerCarton:null,
        unitsPerCarton: x.unitsPerCarton || null,
        tailCartons: Number(x.tailCartons) || 0,
        tailUnitsPerCarton: x.tailCartons > 0 ? Number(x.tailUnitsPerCarton) : null,
        batchNo: x.batchNo,
        materialBatch: isNewFinishedReceipt.value ? "" : x.materialBatch || "",
        productionNumber: x.productionNumber || "",
        sku: isFinishedReceipt.value ? x.sku || "" : "",
        subBatch: isNewFinishedReceipt.value ? "" : x.subBatch || "",
        fullCartons: Number(quantity.fullCartons) || 0,
        looseUnits: Number(quantity.looseUnits) || 0,
        totalUnits:
          quantity.quantityUnknown ? undefined : quantity.manualTotal && quantity.totalUnits !== undefined
            ? Number(quantity.totalUnits)
            : displayedTotal(x),
        quantityUnknown: quantity.quantityUnknown,
        remarks: x.remarks,
        issues: editId ? x.issues : [],
      };
    }),
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
onBeforeRouteLeave(() => true);
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
      palletNo: m.palletNo ?? undefined,
      palletCount: m.palletCount ?? undefined,
      remarks: m.remarks || "",
      items: m.items.map((x) => ({
        id: x.id,
        boxesPerCarton:x.boxesPerCarton,
        unitsPerBox:x.unitsPerBox,
        tailBoxesPerCarton:x.unitsPerBox&&x.tailUnitsPerCarton!=null?x.tailUnitsPerCarton/x.unitsPerBox:null,
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
          productionNumbers: catalog.find(p => p.id === x.productId)?.productionNumbers,
          boxPackings: catalog.find(p=>p.id===x.productId)?.boxPackings,
          cartonSpecs: catalog.find(p => p.id === x.productId)?.cartonSpecs,
          baseUnit: x.baseUnit,
          quantityUnknown: false,
          movementDirection: m.returnMovement ? "WAREHOUSE_TO_PRODUCTION" : m.direction,
          active: true,
        },
        batchNo: x.batchNo,
        materialBatch: x.materialBatch ?? m.manufactureLot ?? "",
        productionNumber: x.productionNumber ?? "",
        sku: x.sku ?? "",
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
    localStorage.removeItem(draftKey.value);
    dirty.value = false;
  }
  draft.value.items.forEach(item => {
    const legacyItem = item as ItemInput & { wholePallet?: boolean };
    item.wholePallets ??= legacyItem.wholePallet ? 1 : 0;
    delete legacyItem.wholePallet;
    item.tailCartons ??= 0;
    item.tailSectionOpen ??= Number(item.tailCartons || 0) > 0 || Number(item.looseUnits || 0) > 0;
  });
  if (!editId && draft.value.items.some(i => i.productId)) {
    const catalog = await productsApi.search('', true, productUsage.value);
    for (const item of draft.value.items) {
      const current = catalog.find(p => p.id === item.productId);
      if (current && item.product) {
        item.product.materialBatches = current.materialBatches;
        item.product.productionNumbers = current.productionNumbers;
        item.product.cartonSpecs = current.cartonSpecs;
        item.product.boxPackings = current.boxPackings;
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
  taskRequest++;
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
    <p v-if="direction === 'PRODUCTION_TO_WAREHOUSE'" class="hint"><span v-if="!draft.returnMovement" class="status ok">成品</span>{{ draft.returnMovement ? '退回物料按正常物料登记，不标记为成品。' : '本次车间入仓的产品将自动标记为成品。' }}</p>
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
      <div v-if="editId" class="grid-2 people">
        <SearchPicker
          v-model="draft.senderPersonId"
          label="发送人"
          placeholder="输入姓名搜索"
          :load="(q) => personsApi.search(q, true).then(rows => rows.filter(p => p.active || p.id === draft.senderPersonId))"
        /><SearchPicker
          v-model="draft.receiverPersonId"
          label="接收人"
          placeholder="输入姓名搜索"
          :load="(q) => personsApi.search(q, true).then(rows => rows.filter(p => p.active || p.id === draft.receiverPersonId))"
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
            :placeholder="isNewFinishedReceipt ? '输入产品名或物料编码' : '输入产品名、物料编码或物料批次'"
            :load="(q) => loadProductChoices(q, item)"
            @select="(p) => selectProduct(item, p)"
          >
            <template #default="{ item: product }">
              <span class="product-option-name">{{ product.name }}</span>
              <span class="product-option-meta">{{ product.materialCode }} · {{ product.movementDirection === 'WAREHOUSE_TO_PRODUCTION' ? '物料' : product.movementDirection === 'PRODUCTION_TO_WAREHOUSE' ? '成品' : '未分类' }}</span>
              <span v-if="product.duplicateName" class="product-option-warning">同名产品，请核对编码</span>
            </template>
          </SearchPicker>
          <small class="hint">未输入时只显示进行中任务的关联{{ productUsage === 'WAREHOUSE_TO_PRODUCTION' ? '物料' : '成品' }}；输入关键词可搜索其他产品。</small>
          <div class="field">
            <label class="required">物料编码</label
            ><input
              class="input mono"
              v-model.trim="item.batchNo"
              placeholder="选择产品后自动带入"
            />
          </div>
          <MaterialBatchPicker v-if="!isNewFinishedReceipt" v-model="item.materialBatch" :batches="item.product?.materialBatches" :required="needsMaterialBatch" />
          <ProductionNumberPicker v-model="item.productionNumber" :numbers="item.product?.productionNumbers" :allow-unspecified="!!item.id && !item.productionNumber" />
          <div v-if="isFinishedReceipt" class="field">
            <label :for="`sku-${index}`">SKU</label>
            <input :id="`sku-${index}`" class="input mono" maxlength="100" v-model.trim="item.sku" placeholder="成品 SKU" />
          </div>
          <div v-if="isFinishedReceipt && index === 0" class="field pallet-number-field">
            <label for="pallet-no">PALLET NO</label>
            <input id="pallet-no" class="input mono" type="number" min="1" step="1" inputmode="numeric" :value="draft.palletNo ?? ''" placeholder="手动输入起始托盘号" @input="changePalletNo" />
            <small class="hint" v-if="editId">{{ draft.palletNo != null ? `原记录 PALLET NO：${palletRangeText}` : '原记录没有 PALLET NO' }}</small>
            <small class="hint">只需填写最小托盘号；托数会自动连续编号。{{ draft.palletNo != null || isNewFinishedReceipt ? `本次编号：${palletRangeText}` : '' }}留空不记录托盘号。</small>
          </div>
          <div v-if="!isNewFinishedReceipt" class="field">
            <label :for="`sub-batch-${index}`">区分批次（可选）</label>
            <input :id="`sub-batch-${index}`" class="input mono" maxlength="100" v-model.trim="item.subBatch" placeholder="外盒同编码、同批次仍需区分时填写" />
            <small class="hint">填写实际区分标识。不同区分批次分别统计；不需要区分时留空。</small>
          </div>
          <div class="packing-fields">
            <p v-if="taskPacking(item)" class="hint">任务包装：{{ taskPacking(item)!.boxesPerCarton }} 盒/箱 × {{ taskPacking(item)!.unitsPerBox }} {{ taskPacking(item)!.primaryMaterialUnit }}/盒。尾箱可填写实际盒数。</p>
            <div v-if="isNewFinishedReceipt" class="field">
              <label class="whole-pallet-toggle"><input type="checkbox" :checked="Number(item.wholePallets || 0) > 0" @change="toggleWholePallet(item,$event,index)" /><span>整托入库</span></label>
              <div v-if="Number(item.wholePallets || 0) > 0" class="whole-pallet-count">
                <label :for="`whole-pallets-${index}`">托数</label>
                <div class="count-stepper"><button type="button" :aria-label="`减少整托数量，第 ${index + 1} 项`" :disabled="Number(item.wholePallets || 0)<=1" @click="changeWholePallets(item,-1)">-</button><input :id="`whole-pallets-${index}`" class="input mono" type="number" min="1" step="1" inputmode="numeric" :value="item.wholePallets || 1" @input="setWholePallets(item,$event)" /><button type="button" :aria-label="`增加整托数量，第 ${index + 1} 项`" @click="changeWholePallets(item,1)">+</button></div>
                <small class="hint">{{ palletCartonCount(item) }} 箱/托，共 {{ wholePalletCartons(item) }} 箱</small>
              </div>
              <small v-if="Number(item.wholePallets || 0) > 0" class="hint">标准托只填写完整托数；最后一托请在下方尾托中填写实际箱数、盒数或数量。</small>
              <div v-if="palletSetupPromptIndex === index" class="pallet-setup-prompt" role="status">
                <p>产品尚未设置每托箱数。设置后才能按整托入库。</p>
                <div><button type="button" class="btn btn-secondary" @click="openProductManagement">去产品管理设置</button><button type="button" class="btn btn-ghost" @click="palletSetupPromptIndex = null">待会设置</button></div>
              </div>
            </div>
            <div v-if="boxed(item)" class="grid-2"><div class="field"><label :for="`box-count-${index}`">每箱装几盒</label><input :id="`box-count-${index}`" class="input" type="number" min="1" step="1" inputmode="numeric" v-model.number="item.boxesPerCarton" @input="quantityChanged(item)" /></div><div class="field"><label :for="`box-units-${index}`">每盒装几{{item.product?.baseUnit||'个'}}</label><input :id="`box-units-${index}`" class="input" type="number" min="1" step="1" inputmode="numeric" v-model.number="item.unitsPerBox" @input="quantityChanged(item)" /></div></div>
            <div class="grid-2">
              <div class="field"><label :for="`size-${index}`" :class="{required:item.fullCartons > 0 && commonSpecs(item).length > 1}">{{boxed(item)?'每箱数量（自动计算）':'完整箱每箱数量'}}</label><input :id="`size-${index}`" class="input mono" type="number" min="1" step="1" inputmode="numeric" v-model.number="item.unitsPerCarton" :readonly="!!taskPacking(item)||boxed(item)" @input="quantityChanged(item)" placeholder="选择下方规格或输入每箱数量" /><small v-if="!taskPacking(item) && commonSpecs(item).length > 1" class="hint">有多种每箱数量，请选择本次完整箱规格；仅尾箱或散装可留空。</small></div>
              <div class="field"><label :for="`full-${index}`">额外完整箱数</label><input :id="`full-${index}`" class="input mono" type="number" min="0" step="1" inputmode="numeric" v-model.number="item.fullCartons" :disabled="isNewFinishedReceipt && Number(item.wholePallets || 0) > 0" @input="quantityChanged(item)" /></div>
            </div>
            <div v-if="!taskPacking(item) && item.product?.boxPackings?.length" class="spec-options" aria-label="选择包装规格"><button v-for="packing in item.product.boxPackings" :key="`${packing.boxesPerCarton}/${packing.unitsPerBox}`" type="button" :aria-pressed="item.boxesPerCarton===packing.boxesPerCarton&&item.unitsPerBox===packing.unitsPerBox" :class="{active:item.boxesPerCarton===packing.boxesPerCarton&&item.unitsPerBox===packing.unitsPerBox}" @click="choosePacking(item,packing)">{{packing.boxesPerCarton}} 盒 × {{packing.unitsPerBox}} {{item.product.baseUnit}}/盒</button></div>
            <button v-if="productUsage==='PRODUCTION_TO_WAREHOUSE'&&!taskPacking(item)&&!boxed(item)" type="button" class="btn btn-ghost" @click="choosePacking(item,{boxesPerCarton:0,unitsPerBox:0})">填写每箱盒数 / 每盒数量</button>
            <p v-if="productUsage==='PRODUCTION_TO_WAREHOUSE'&&!taskPacking(item)&&!boxed(item)" class="hint">旧规格只有总数，补录盒数与每盒数量后，尾箱即可按盒计算。</p>
            <div v-if="!taskPacking(item) && legacySpecs(item).length" class="spec-options" aria-label="选择每箱数量">
              <small class="hint">预设规格</small><button v-for="size in legacySpecs(item)" :key="size" type="button" :class="{active:item.unitsPerCarton===size}" :aria-pressed="item.unitsPerCarton===size" @click="chooseLegacy(item,size)">{{size}} {{item.product?.baseUnit || '个'}}/箱</button>
            </div>
            <details :open="item.tailSectionOpen ?? (item.tailCartons>0 || item.looseUnits>0)" class="optional-packing" @toggle="tailSectionToggled(item,$event)">
              <summary>{{ isNewFinishedReceipt ? '尾箱（可选）' : '尾箱 / 真正散装（可选）' }}</summary>
              <div class="grid-2">
                <div class="field"><label :for="`tail-${index}`">尾箱箱数</label><div class="count-stepper"><button type="button" aria-label="减少尾箱箱数" :disabled="Number(item.tailCartons || 0)<=0" @click="changeTailCartons(item,-1)">-</button><input :id="`tail-${index}`" class="input mono" type="number" min="0" step="1" inputmode="numeric" v-model.number="item.tailCartons" @input="tailCartonsChanged(item)" /><button type="button" aria-label="增加尾箱箱数" @click="changeTailCartons(item,1)">+</button></div></div>
                <div v-if="taskPacking(item)||boxed(item)" class="field"><label :for="`tail-boxes-${index}`">每个尾箱实际盒数</label><input :id="`tail-boxes-${index}`" class="input mono" type="number" min="1" step="1" :value="tailBoxes(item)" @input="setTailBoxes(item, $event)" placeholder="例如 12 盒" /><small class="hint">实际 {{ item.tailUnitsPerCarton || 0 }} {{ item.product?.baseUnit }} / 尾箱</small></div>
                <div v-else class="field"><label :for="`tail-size-${index}`">每个尾箱实际数量</label><input :id="`tail-size-${index}`" class="input mono" type="number" min="1" step="1" inputmode="numeric" v-model.number="item.tailUnitsPerCarton" @input="quantityChanged(item)" placeholder="例如 3" /></div>
              </div>
              <small class="hint">{{ isNewFinishedReceipt ? '尾箱计入总箱数；不同规格的尾箱请分开填写。' : '尾箱计入总箱数。尾箱数量不同时，点击“添加同物料规格 / 批次”分开填写。' }}</small>
              <div v-if="!isNewFinishedReceipt" class="field"><label :for="`loose-${index}`">真正散装（未装箱的{{item.product?.baseUnit || '数量'}}）</label><input :id="`loose-${index}`" class="input mono" type="number" min="0" step="1" inputmode="numeric" v-model.number="item.looseUnits" @input="quantityChanged(item)" /></div>
            </details>
            <button v-if="item.productId" class="btn btn-ghost" type="button" @click="addSpecification(item)">{{ isNewFinishedReceipt ? '＋ 添加同产品规格' : '＋ 添加同物料规格 / 批次' }}</button>
          </div>
          <label v-if="!isNewFinishedReceipt" class="unknown-quantity">
            <input type="checkbox" v-model="item.quantityUnknown" :disabled="item.product?.quantityUnknown" @change="toggleUnknownQuantity(item)" />
            {{item.product?.quantityUnknown ? '该产品已设置为数量不确定' : '数量不确定（无法清点或没有数量标识）'}}
          </label>
          <div class="total-box">
            <span>总箱数：{{ cartonCount(quantityItem(item)) }} 箱（<template v-if="wholePalletCartons(item)">{{ item.wholePallets }} 托 × {{ palletCartonCount(item) }} 箱 + </template>{{ item.fullCartons || 0 }} 额外满箱 + {{ item.tailCartons || 0 }} 尾箱）</span>
            <span v-if="direction==='PRODUCTION_TO_WAREHOUSE'&&!draft.returnMovement&&(taskPacking(item)||item.boxesPerCarton&&item.unitsPerBox)">总盒数：{{ (quantityItem(item).fullCartons * (taskPacking(item)?.boxesPerCarton || item.boxesPerCarton || 0) + (item.tailCartons || 0) * (tailBoxes(item) || 0)).toLocaleString() }} 盒</span>
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
              v-if="item.manualTotal && calculate(quantityItem(item)) != null"
              class="hint"
              >按箱规应为 {{ calculate(quantityItem(item)) }}，当前填写
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
        <small v-else class="hint">{{ direction === 'WAREHOUSE_TO_PRODUCTION' ? '只显示需要本单全部物料且进行中的任务。' : draft.returnMovement ? '退料计入任务的净发料，不计入成品产量。' : '按任务对应成品累计回仓产量；已完成任务请从任务详情补关联已有记录。' }}</small>
        <p v-if="taskError" class="form-alert" role="alert">{{ taskError }} <button type="button" class="btn btn-ghost" @click="refreshTasks">重试</button></p>
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
.product-option-name{display:block;font-weight:650;overflow-wrap:anywhere}
.product-option-meta{display:block;margin-top:3px;font-size:12px;color:var(--muted);overflow-wrap:anywhere}
.product-option-warning{display:block;margin-top:3px;font-size:12px;color:var(--orange)}
.packing-fields > .grid-2 { align-items: start; }
.pallet-number-field{max-width:420px}
.whole-pallet-toggle{display:flex;align-items:center;gap:9px;min-height:40px;font-weight:650;color:var(--ink)}
.whole-pallet-toggle input{width:18px;height:18px;accent-color:var(--blue)}
.whole-pallet-count{display:grid;gap:8px;margin:8px 0 0 27px;max-width:320px}
.pallet-setup-prompt{margin-top:10px;padding:11px;border:1px solid #e3b56d;border-radius:7px;background:#fff8e8;color:#744a10}
.pallet-setup-prompt p{margin:0 0 9px;font-size:13px}
.pallet-setup-prompt>div{display:flex;flex-wrap:wrap;gap:7px}

.packing-fields{display:grid;gap:12px}.spec-options{display:flex;flex-wrap:wrap;gap:6px;align-items:center}.spec-options button{border:1px solid var(--line);background:#fff;border-radius:6px;padding:7px 10px;min-height:36px;font-size:12px}.spec-options button.active{background:var(--soft-blue);border-color:var(--blue);color:var(--blue)}
.count-stepper{display:grid;grid-template-columns:40px minmax(0,1fr) 40px;align-items:center;gap:6px}.count-stepper button{width:40px;height:44px;border:1px solid var(--line);border-radius:6px;background:var(--panel);color:var(--blue);font-size:20px;line-height:1;cursor:pointer}.count-stepper button:disabled{color:var(--muted);cursor:not-allowed}.count-stepper input{min-width:0;text-align:center}
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
