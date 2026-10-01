<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { onBeforeRouteLeave, useRoute, useRouter } from "vue-router";
import SearchPicker from "../components/SearchPicker.vue";
import { errorMessage } from "../api/http";
import { movementsApi, personsApi, productsApi } from "../api";
import type {
  Direction,
  IssueType,
  ItemInput,
  MovementDraft,
  Product,
} from "../types";
import { issueLabels, directionLabel } from "../utils/labels";
import { nowLocalInput, toInstant, toLocalInput } from "../utils/time";
import { createUuid } from "../utils/uuid";
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
  fullCartons: 0,
  looseUnits: 0,
  remarks: "",
  issues: [],
  manualTotal: false,
});
const draft = ref<MovementDraft>({
  idempotencyKey: createUuid(),
  direction: direction.value,
  movementTime: nowLocalInput(),
  manufactureLot: "",
  remarks: "",
  items: [blankItem()],
});
const files = ref<
  {
    file: File;
    url: string;
    status: "ready" | "uploading" | "failed";
    progress: number;
  }[]
>([]);
const saving = ref(false),
  dirty = ref(false),
  message = ref("");
const savedId = ref<number>();
const draftKey = computed(() => `movement-draft:${direction.value}`);
const allIssues = Object.entries(issueLabels) as [IssueType, string][];
function calculate(item: ItemInput) {
  const size = item.product?.defaultUnitsPerCarton;
  if (size != null) return item.fullCartons * size + item.looseUnits;
  if (item.fullCartons === 0) return item.looseUnits;
  return undefined;
}
function displayedTotal(item: ItemInput) {
  return item.manualTotal ? item.totalUnits : calculate(item);
}
function selectProduct(item: ItemInput, p?: Product) {
  item.product = p;
  item.productId = p?.id;
  item.batchNo = p?.materialCode || "";
  if (
    p?.materialBatch &&
    direction.value === "WAREHOUSE_TO_PRODUCTION" &&
    !draft.value.manufactureLot
  )
    draft.value.manufactureLot = p.materialBatch;
  item.manualTotal = false;
  item.totalUnits = calculate(item);
}
function quantityChanged(item: ItemInput) {
  if (!item.manualTotal) item.totalUnits = calculate(item);
}
function toggleIssue(item: ItemInput, type: IssueType, checked: boolean) {
  if (checked) item.issues.push({ type });
  else item.issues = item.issues.filter((x) => x.type !== type);
}
function issue(item: ItemInput, type: IssueType) {
  return item.issues.find((x) => x.type === type);
}
function pickFiles(event: Event) {
  const input = event.target as HTMLInputElement;
  for (const file of Array.from(input.files || [])) {
    if (files.value.length >= 10) break;
    files.value.push({
      file,
      url: URL.createObjectURL(file),
      status: "ready",
      progress: 0,
    });
  }
  input.value = "";
}
function removeFile(index: number) {
  if (!confirm("删除这张待上传照片？")) return;
  URL.revokeObjectURL(files.value[index].url);
  files.value.splice(index, 1);
}
function validate() {
  if (!draft.value.senderPersonId || !draft.value.receiverPersonId)
    return "请选择发送人和接收人。";
  if (
    direction.value === "WAREHOUSE_TO_PRODUCTION" &&
    !draft.value.manufactureLot.trim()
  )
    return "请填写物料批次。";
  for (let i = 0; i < draft.value.items.length; i++) {
    const x = draft.value.items[i];
    if (!x.productId) return `请选择产品明细 ${i + 1} 的产品。`;
    if (!x.batchNo.trim()) return `请填写产品明细 ${i + 1} 的物料编码。`;
    if (
      x.fullCartons <= 0 &&
      x.looseUnits <= 0 &&
      (!x.totalUnits || x.totalUnits <= 0)
    )
      return `产品明细 ${i + 1} 至少填写一种数量。`;
    if (issue(x, "OTHER") && !issue(x, "OTHER")?.description?.trim())
      return `请填写产品明细 ${i + 1} 的“其他”异常说明。`;
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
    manufactureLot:
      direction.value === "WAREHOUSE_TO_PRODUCTION"
        ? draft.value.manufactureLot
        : undefined,
    remarks: draft.value.remarks,
    items: draft.value.items.map((x) => ({
      productId: x.productId,
      batchNo: x.batchNo,
      fullCartons: Number(x.fullCartons) || 0,
      looseUnits: Number(x.looseUnits) || 0,
      totalUnits:
        x.manualTotal && x.totalUnits !== undefined
          ? Number(x.totalUnits)
          : displayedTotal(x),
      remarks: x.remarks,
      issues: x.issues,
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
  saving.value = true;
  try {
    const result = editId
      ? await movementsApi.update(editId, payload())
      : await movementsApi.create(payload());
    savedId.value = result.id;
    localStorage.removeItem(draftKey.value);
    localStorage.setItem(
      `last-persons:${direction.value}`,
      JSON.stringify({
        sender: result.senderPersonId,
        receiver: result.receiverPersonId,
      }),
    );
    dirty.value = false;
    const failed = await uploadPending();
    if (!failed) await router.replace(`/movements/${result.id}`);
    else message.value = "记录已保存，但有照片上传失败。请点击“重试上传”。";
  } catch (e) {
    message.value = errorMessage(e);
  } finally {
    saving.value = false;
  }
}
async function uploadPending() {
  const pending = files.value.filter((x) => x.status !== "uploading");
  let next = 0;
  let failed = 0;
  async function worker() {
    while (next < pending.length) {
      const entry = pending[next++];
      entry.status = "uploading";
      try {
        await movementsApi.uploadPhoto(
          savedId.value!,
          entry.file,
          (p) => (entry.progress = p),
        );
        entry.status = "ready";
        entry.progress = 100;
      } catch {
        entry.status = "failed";
        failed++;
      }
    }
  }
  await Promise.all(Array.from({ length: Math.min(3, pending.length) }, () => worker()));
  return failed;
}
async function retry() {
  saving.value = true;
  const failed = await uploadPending();
  saving.value = false;
  if (!failed) {
    dirty.value = false;
    await router.replace(`/movements/${savedId.value}`);
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
    direction.value = m.direction;
    draft.value = {
      idempotencyKey: createUuid(),
      direction: m.direction,
      movementTime: toLocalInput(m.movementTime),
      senderPersonId: m.senderPersonId,
      receiverPersonId: m.receiverPersonId,
      manufactureLot: m.manufactureLot || "",
      remarks: m.remarks || "",
      items: m.items.map((x) => ({
        productId: x.productId,
        product: {
          id: x.productId,
          name: x.productName,
          materialCode: x.batchNo,
          defaultUnitsPerCarton: x.unitsPerCarton,
          baseUnit: x.baseUnit,
          active: true,
        },
        batchNo: x.batchNo,
        fullCartons: x.fullCartons,
        looseUnits: x.looseUnits,
        totalUnits: x.totalUnits,
        manualTotal: x.totalUnitsOverridden,
        remarks: x.remarks || "",
        issues: x.issues.map((y) => ({
          type: y.type,
          description: y.description,
        })),
      })),
    };
    dirty.value = false;
  } else {
    const cached = localStorage.getItem(draftKey.value);
    if (cached && confirm("发现未保存的表单草稿，是否继续填写？"))
      draft.value = JSON.parse(cached);
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
});
onBeforeUnmount(() => {
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
            :load="(q) => productsApi.search(q)"
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
          <div
            v-if="index === 0 && direction === 'WAREHOUSE_TO_PRODUCTION'"
            class="field"
          >
            <label class="required">物料批次</label
            ><input
              class="input mono"
              v-model.trim="draft.manufactureLot"
              placeholder="例如 CA202607006"
            />
            <small class="hint">本次流转的所有产品共用</small>
          </div>
          <div class="grid-2">
            <div class="field">
              <label>完整箱</label
              ><input
                class="input mono"
                type="number"
                inputmode="numeric"
                min="0"
                step="1"
                v-model.number="item.fullCartons"
                @input="quantityChanged(item)"
              />
            </div>
            <div class="field">
              <label
                >散装数量<span v-if="item.product"
                  >（{{ item.product.baseUnit }}）</span
                ></label
              ><input
                class="input mono"
                type="number"
                inputmode="numeric"
                min="0"
                step="1"
                v-model.number="item.looseUnits"
                @input="quantityChanged(item)"
              />
            </div>
          </div>
          <div class="total-box">
            <span>总数量</span
            ><strong class="mono"
              >{{ displayedTotal(item) ?? "未知"
              }}<small v-if="displayedTotal(item) != null">
                {{ item.product?.baseUnit || "个" }}</small
              ></strong
            ><label class="manual"
              ><input type="checkbox" v-model="item.manualTotal" />
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
                !item.product.defaultUnitsPerCarton &&
                item.fullCartons > 0
              "
              class="hint"
              >此产品没有默认箱规，可保存为总数量未知。</small
            >
          </div>
          <fieldset class="issues">
            <legend>异常（可多选）</legend>
            <label v-for="[type, label] in allIssues" :key="type"
              ><input
                type="checkbox"
                :checked="!!issue(item, type)"
                @change="
                  toggleIssue(
                    item,
                    type,
                    ($event.target as HTMLInputElement).checked,
                  )
                "
              />{{ label }}</label
            ><textarea
              v-if="issue(item, 'OTHER')"
              class="textarea"
              v-model="issue(item, 'OTHER')!.description"
              placeholder="请说明其他异常"
            ></textarea>
          </fieldset>
          <div class="field">
            <label>产品备注</label
            ><textarea
              class="textarea"
              v-model.trim="item.remarks"
              placeholder="仅填写这个产品的补充情况"
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
      <div class="field">
        <label>整单备注</label
        ><textarea
          class="textarea"
          v-model.trim="draft.remarks"
          placeholder="例如：10:20 送到车间门口"
        ></textarea>
      </div>
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
            ><span v-if="entry.status === 'uploading'"
              >{{ entry.progress }}%</span
            ><span v-if="entry.status === 'failed'" class="failed">失败</span>
          </div>
        </div>
      </div>
    </section>
    <div class="sticky-actions">
      <div v-if="message" class="sticky-alert" role="alert">
        {{ message }}
      </div>
      <button
        v-if="savedId"
        class="btn btn-primary btn-block"
        :disabled="saving"
        @click="retry"
      >
        {{ saving ? "正在上传…" : "重试上传失败照片" }}</button
      ><button
        v-else
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
.issues input {
  width: 18px;
  height: 18px;
  accent-color: #174a68;
}
.issues {
  border: 0;
  padding: 0;
  margin: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 7px;
}
.issues legend {
  font-size: 14px;
  font-weight: 680;
  margin-bottom: 8px;
}
.issues label {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 38px;
  font-size: 14px;
}
.issues .textarea {
  grid-column: 1/-1;
}
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
