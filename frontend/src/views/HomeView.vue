<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import MaterialSummary from "../components/MaterialSummary.vue";
import MovementCard from "../components/MovementCard.vue";
import RecordOriginals from "../components/RecordOriginals.vue";
import { movementsApi } from "../api";
import type { Direction, MovementSummary, QueryStats, StatMovement } from "../types";
import { today, showDateTime } from "../utils/time";
import { useAuth } from "../auth";
import { errorMessage } from "../api/http";
import { HOME_FILTERS_KEY, readHomeFilters, presetRange, singleDayRange, validDate } from "../utils/homeFilters";
import type { DatePreset } from "../utils/homeFilters";
let storedFilters: string | null = null;
try { storedFilters = sessionStorage.getItem(HOME_FILTERS_KEY); } catch { /* Storage can be disabled in the browser. */ }
const initialFilters = readHomeFilters(storedFilters, today());
const { authenticated } = useAuth();
const router = useRouter();
const recordDialog = ref<HTMLDialogElement>();
type ProductStat = QueryStats["directions"][number]["products"][number];
const selectedProduct = ref<ProductStat>();
function statIdentifiers(product: ProductStat) {
  // A material group can contain both normal receipts and returns; keep both identifiers in that case.
  return {
    showMaterialCode: product.movements.some(m => m.direction === 'PRODUCTION_TO_WAREHOUSE' && !m.returnMovement),
    showMaterialBatch: product.movements.some(m => m.direction !== 'PRODUCTION_TO_WAREHOUSE' || m.returnMovement),
  };
}
const sourceMovements = ref<StatMovement[]>([]);
function openProduct(product: ProductStat) {
  if (product.movements.length === 1) {
    void router.push(`/movements/${product.movements[0].id}`);
    return;
  }
  selectedProduct.value = product;
  sourceMovements.value = product.movements;
  recordDialog.value?.showModal();
}
function openMovement(id: number) {
  recordDialog.value?.close();
  void router.push(`/movements/${id}`);
}
const error = ref("");
let searchTimer: ReturnType<typeof setTimeout> | undefined;
const rows = ref<MovementSummary[]>([]),
  stats = ref<QueryStats>(),
  appliedFrom = ref(initialFilters.from),
  appliedTo = ref(initialFilters.to),
  loading = ref(false),
  from = ref(initialFilters.from),
  to = ref(initialFilters.to),
  direction = ref<Direction>(initialFilters.direction),
  q = ref(initialFilters.q),
  moreFilters = ref(initialFilters.moreFilters),
  activePreset = ref<DatePreset>(initialFilters.activePreset);
const singleDate = ref(initialFilters.activePreset === 'single' ? initialFilters.from : '');
watch([from, to, direction, q, moreFilters, activePreset], () => {
  if (!validDate(from.value) || !validDate(to.value) || from.value > to.value) return;
  try {
    sessionStorage.setItem(HOME_FILTERS_KEY, JSON.stringify({ from: from.value, to: to.value,
      direction: direction.value, q: q.value, moreFilters: moreFilters.value, activePreset: activePreset.value }));
  } catch { /* Filtering still works without browser storage. */ }
}, { flush: 'sync' });
let requestId = 0;
async function load() {
  recordDialog.value?.close();
  const currentRequest = ++requestId;
  clearTimeout(searchTimer);
  loading.value = false;
  rows.value = [];
  stats.value = undefined;
  error.value = "";
  if (!from.value || !to.value || from.value > to.value) {
    error.value = "请选择有效的日期范围，开始日期不能晚于结束日期。";
    return;
  }
  loading.value = true;
  try {
    const params = {
      from: from.value,
      to: to.value,
      direction: direction.value,
      status: "ACTIVE" as const,
      q: q.value,
    };
    const [page, currentStats] = await Promise.all([
      movementsApi.list({ ...params, page: 0, size: 100 }),
      movementsApi.queryStats(params),
    ]);
    if (currentRequest !== requestId) return;
    const content = [...page.content];
    for (let index = 1; index < page.totalPages; index++) {
      const next = await movementsApi.list({ ...params, page: index, size: 100 });
      if (currentRequest !== requestId) return;
      content.push(...next.content);
    }
    rows.value = content;
    stats.value = currentStats;
    appliedFrom.value = params.from;
    appliedTo.value = params.to;
  } catch (cause) {
    if (currentRequest === requestId) error.value = errorMessage(cause);
  } finally {
    if (currentRequest === requestId) loading.value = false;
  }
}
function selectDirection(value: Direction) {
  direction.value = value;
  void load();
}
function preset(kind: 'today' | 'yesterday' | 'week' | 'twoWeeks') {
  const range = presetRange(kind, today());
  from.value = range.from;
  to.value = range.to;
  activePreset.value = kind;
  singleDate.value = '';
  void load();
}
function selectSingleDay() {
  if (!validDate(singleDate.value)) {
    singleDate.value = activePreset.value === 'single' ? from.value : '';
    return;
  }
  const range = singleDayRange(singleDate.value);
  from.value = range.from;
  to.value = range.to;
  activePreset.value = 'single';
  void load();
}
function customDate() {
  activePreset.value = 'range';
  singleDate.value = '';
  to.value = today();
  void load();
}
watch(q, () => {
  clearTimeout(searchTimer);
  ++requestId;
  rows.value = [];
  stats.value = undefined;
  error.value = "";
  loading.value = true;
  searchTimer = setTimeout(() => void load(), 300);
});
onBeforeUnmount(() => { clearTimeout(searchTimer); ++requestId; });
onMounted(load);
const directionLabel = (value: Direction) => value === "WAREHOUSE_TO_PRODUCTION" ? "仓库 → 生产车间" : "生产车间 → 仓库";
const formatNumber = (value: number) => value.toLocaleString("zh-CN");
</script>
<template>
  <div class="page">
    <div class="section-head page-heading">
      <h1 class="page-title">流转记录</h1>
      <div v-if="authenticated" class="create-actions">
        <RouterLink class="btn btn-primary" to="/movements/new/warehouse-to-production">新增出库</RouterLink>
        <RouterLink class="btn btn-secondary" to="/movements/new/production-to-warehouse">新增入库 / 退回</RouterLink>
      </div>
    </div>
    <section class="filters card card-pad">
      <div class="preset">
        <button class="quick-date" :class="{active:activePreset==='today'}" :aria-pressed="activePreset==='today'" @click="preset('today')">今天</button
        ><button class="quick-date" :class="{active:activePreset==='yesterday'}" :aria-pressed="activePreset==='yesterday'" @click="preset('yesterday')">昨天</button
        ><button class="quick-date" :class="{active:activePreset==='week'}" :aria-pressed="activePreset==='week'" @click="preset('week')">本周</button
        ><button class="quick-date" :class="{active:activePreset==='twoWeeks'}" :aria-pressed="activePreset==='twoWeeks'" @click="preset('twoWeeks')">两周</button>
      </div>
      <div class="field single-day" :class="{selected:activePreset==='single'}">
        <label for="single-day">只看某一天</label>
        <input id="single-day" class="input" type="date" v-model="singleDate" :max="today()" @change="selectSingleDay" />
      </div>
      <div class="field">
        <label>方向</label>
        <div class="direction-choice" role="group" aria-label="流转方向筛选">
          <button type="button" :class="{active:direction==='WAREHOUSE_TO_PRODUCTION'}" :aria-pressed="direction==='WAREHOUSE_TO_PRODUCTION'" @click="selectDirection('WAREHOUSE_TO_PRODUCTION')">仓库 → 生产车间</button>
          <button type="button" class="return" :class="{active:direction==='PRODUCTION_TO_WAREHOUSE'}" :aria-pressed="direction==='PRODUCTION_TO_WAREHOUSE'" @click="selectDirection('PRODUCTION_TO_WAREHOUSE')">车间 → 仓库</button>
        </div>
      </div>
      <div class="field">
        <label for="material-search">搜索物料</label>
        <input id="material-search" class="input" v-model.trim="q" placeholder="物料名称、编码、批次或区分批次" @keyup.enter="load" />
      </div>
      <button class="more-toggle" type="button" :aria-expanded="moreFilters" aria-controls="advanced-filters" @click="moreFilters=!moreFilters">
        <span>更多筛选</span><span aria-hidden="true">{{moreFilters ? '收起' : '展开'}} {{moreFilters ? '⌃' : '⌄'}}</span>
      </button>
      <div v-show="moreFilters" id="advanced-filters" class="advanced-filters">
        <div class="field">
          <label for="range-start">开始日期</label
          ><input id="range-start" class="input" type="date" v-model="from" :max="today()" @change="customDate" />
          <small class="hint">自定义日期查询至今天</small>
        </div>
      </div>
    </section>
    <RecordOriginals :from="from" :to="to" :direction="direction" />
    <section class="section">
      <div v-if="!loading && !error" class="query-stats card">
        <div class="stats-heading">
          <strong>查询范围统计</strong>
          <span>{{ appliedFrom }} ～ {{ appliedTo }}</span>
        </div>
        <div v-if="!stats?.directions.length" class="stats-empty">当前查询范围暂无统计数据</div>
        <div v-else>
          <section v-for="group in stats.directions" :key="group.direction" class="direction-stat">
            <h3>{{ directionLabel(group.direction) }}</h3>
            <div class="stats-total"><strong>共 {{ formatNumber(group.products.length) }} 组物料 / 批次</strong><span>·</span><strong>{{ formatNumber(group.totalCartons) }} 箱</strong><span v-if="group.unknownItemCount">· 含 {{ group.unknownItemCount }} 项数量不确定</span></div>
            <div>
              <button v-for="product in group.products" :key="JSON.stringify([product.productName, product.materialCode, product.materialBatch, product.subBatch, product.baseUnit])" type="button" class="product-stat" @click="openProduct(product)">
                <MaterialSummary v-bind="{ ...product, ...statIdentifiers(product) }" />
                <span class="record-arrow" aria-hidden="true">›</span>
              </button>
            </div>
          </section>
        </div>
      </div>
      <div class="section-head"><h2 class="section-title">记录列表</h2><span v-if="!loading && !error" class="hint">{{ rows.length }} 条</span></div>
      <div v-if="error" class="card empty" role="alert">{{ error }} <button type="button" class="btn" @click="load">重试</button></div>
      <div v-else-if="loading" class="card empty" role="status">正在查询…</div>
      <div v-else-if="!rows.length" class="card empty">
        没有符合条件的记录。
      </div>
      <div v-else class="stack record-stack">
        <MovementCard v-for="row in rows" :key="row.id" :movement="row" show-date />
      </div>
    </section>
    <nav class="quick-links card" aria-label="管理与导出"><RouterLink to="/production-tasks">生产任务</RouterLink><RouterLink to="/reports">导出 PDF</RouterLink><RouterLink v-if="authenticated" to="/products">产品管理</RouterLink><RouterLink v-if="authenticated" to="/persons">人员管理</RouterLink></nav>
  </div>
    <dialog ref="recordDialog" class="record-dialog" aria-labelledby="record-dialog-title" @click="event => { if (event.target === recordDialog) recordDialog?.close(); }">
      <div class="dialog-heading"><h2 id="record-dialog-title">选择流转记录</h2><button type="button" class="btn" autofocus @click="recordDialog?.close()">关闭</button></div>
      <p class="dialog-summary">{{ selectedProduct?.productName }} · 共 {{ sourceMovements.length }} 个流转单</p>
      <div class="source-list">
        <button v-for="movement in sourceMovements" :key="movement.id" type="button" class="source-record" @click="openMovement(movement.id)">
          <MaterialSummary v-if="selectedProduct" :show-material-code="movement.direction === 'PRODUCTION_TO_WAREHOUSE' && !movement.returnMovement" :show-material-batch="movement.direction !== 'PRODUCTION_TO_WAREHOUSE' || movement.returnMovement" :product-name="selectedProduct.productName" :material-code="selectedProduct.materialCode" :material-batch="selectedProduct.materialBatch" :sub-batch="selectedProduct.subBatch" :base-unit="selectedProduct.baseUnit" :full-cartons="movement.fullCartons" :tail-cartons="movement.tailCartons" :total-cartons="movement.totalCartons" :packaging="movement.packaging" :loose-units="movement.looseUnits" :total-quantity="movement.totalQuantity" :unknown-item-count="movement.unknownItemCount" />
          <div class="source-meta">
            <time :datetime="movement.movementTime">{{ showDateTime(movement.movementTime) }}</time>
            <span :class="{ 'return-direction': movement.direction === 'PRODUCTION_TO_WAREHOUSE' }">{{ directionLabel(movement.direction) }}<template v-if="movement.returnMovement"> · 退回</template></span>
            <small v-if="movement.photoCount" class="photo-tag">照片 {{ movement.photoCount }}</small>
          </div>
        </button>
      </div>
    </dialog>
</template>
<style scoped>
.page-heading { flex-wrap: wrap; gap: 12px; }
.page-heading .page-title { margin: 0; }
.create-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.quick-links { margin-top: 22px; display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; }
.quick-links a { min-height: 52px; display: grid; place-items: center; font-weight: 680; }
.quick-links a + a { border-left: 1px solid #dce2e5; }
.filters {
  display: grid;
  gap: 13px;
}
.single-day{grid-template-columns:auto minmax(0,1fr);align-items:center;gap:12px}.single-day.selected label{color:#267566}.single-day input{min-width:0;width:100%;box-sizing:border-box}
.preset {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 5px;
  padding: 4px;
  border-radius: 11px;
  background: #e7edef;
}
.quick-date {
  min-height: 42px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #60737d;
  font: inherit;
  font-size: 14px;
  font-weight: 760;
  cursor: pointer;
}
.quick-date.active { background: #fff; color: #174a68; box-shadow: 0 2px 8px rgba(32,53,64,.14); }
.quick-date:focus-visible,.more-toggle:focus-visible,.direction-choice button:focus-visible { outline: 3px solid rgba(31,107,138,.3); outline-offset: 2px; }
.direction-choice { display: grid; grid-template-columns: repeat(2,1fr); gap: 4px; padding: 4px; border-radius: 11px; background: #e7edef; }
.direction-choice button { min-height: 44px; border: 0; border-radius: 8px; padding: 8px 4px; background: transparent; color: #60737d; font: inherit; font-size: 13px; font-weight: 760; cursor: pointer; }
.direction-choice button.active { background: #fff; color: #174a68; box-shadow: 0 2px 8px rgba(32,53,64,.14); }
.direction-choice button.return.active { color: #267566; }
.more-toggle {
  min-height: 42px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border: 0;
  border-top: 1px solid #e1e7e9;
  padding: 10px 2px 0;
  background: transparent;
  color: #174a68;
  font: inherit;
  font-size: 14px;
  font-weight: 720;
  cursor: pointer;
}
.more-toggle span:last-child { color: #6c7d85; font-size: 12px; font-weight: 600; }
.advanced-filters { display: grid; gap: 13px; padding: 13px; border-radius: 10px; background: #f4f7f8; }
.query-stats { margin-bottom: 14px; overflow: hidden; }
.stats-heading { display: flex; justify-content: space-between; gap: 12px; padding: 14px 16px; background: #f3f7f8; color: #174a68; }
.stats-heading span { color: #687982; font-size: 13px; }
.stats-empty { padding: 24px 16px; text-align: center; color: #66747c; }
.direction-stat { padding: 15px 16px; border-top: 1px solid #e2e8ea; }
.direction-stat:first-child { border-top: 0; }
.direction-stat h3 { margin: 0 0 8px; font-size: 15px; color: #243e4b; }
.stats-total { display: flex; flex-wrap: wrap; gap: 7px; align-items: center; margin-bottom: 7px; color: #52626b; }
.stats-total strong { color: #174a68; font-size: 17px; }
.product-stat { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 16px; padding: 12px 4px; border: 0; border-top: 1px solid #edf0f1; font: inherit; font-size: 14px; width: 100%; text-align: left; background: transparent; color: inherit; cursor: pointer; align-items: center; }
.product-stat:hover,.source-record:hover { background: #edf5f7; }
.product-stat:focus-visible,.source-record:focus-visible { outline: 3px solid #267566; outline-offset: -3px; }
.product-stat .record-arrow { color: #174a68; font-size: 22px; }
.record-dialog { width: min(620px, calc(100% - 24px)); max-height: 85dvh; box-sizing: border-box; padding: 20px; border: 1px solid #b9c7ce; border-radius: 14px; color: #243e4b; }
.record-dialog::backdrop { background: rgba(20, 40, 50, .5); }
.dialog-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.dialog-heading h2 { margin: 0; font-size: 20px; }
.dialog-summary { overflow-wrap: anywhere; color: #60737d; }
.source-list { display: grid; gap: 10px; }
.source-record { display: grid; gap: 8px; width: 100%; padding: 14px; border: 1px solid #dce2e5; border-radius: 9px; background: #fff; color: inherit; text-align: left; font: inherit; cursor: pointer; overflow-wrap: anywhere; }
.source-meta { display:flex; flex-wrap:wrap; align-items:center; gap:5px 12px; font-size:12px; color:var(--muted); }
.source-meta .return-direction { color:var(--green); }
.photo-tag { padding:2px 6px; border-radius:5px; background:#edf0f2; font-size:11px; }
.record-stack { gap: 10px; }
@media (max-width: 560px) {
  .direction-choice button { font-size: 11px; }
  .stats-heading { align-items: flex-start; flex-direction: column; gap: 4px; }
  .product-stat { gap: 8px; font-size: 13px; }
  .record-dialog { padding: 14px; }
  .source-record { padding: 11px; }
}
</style>
