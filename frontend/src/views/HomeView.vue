<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import MovementCard from "../components/MovementCard.vue";
import { movementsApi } from "../api";
import type { Direction, MovementSummary, QueryStats, StatMovement } from "../types";
import { today, showDateTime } from "../utils/time";
import { useAuth } from "../auth";
import { errorMessage } from "../api/http";
const { authenticated } = useAuth();
const router = useRouter();
const recordDialog = ref<HTMLDialogElement>();
const selectedProduct = ref("");
const sourceMovements = ref<StatMovement[]>([]);
function openProduct(product: QueryStats["directions"][number]["products"][number]) {
  if (product.movements.length === 1) {
    void router.push(`/movements/${product.movements[0].id}`);
    return;
  }
  selectedProduct.value = product.productName;
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
  appliedFrom = ref(today()),
  appliedTo = ref(today()),
  loading = ref(false),
  from = ref(today()),
  to = ref(today()),
  direction = ref<Direction>("WAREHOUSE_TO_PRODUCTION"),
  q = ref(""),
  moreFilters = ref(false),
  activePreset = ref<"today" | "yesterday" | "week" | "twoWeeks" | undefined>("today");
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
function shiftDate(date: string, days: number) {
  const [year, month, day] = date.split("-").map(Number);
  const value = new Date(Date.UTC(year, month - 1, day));
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}
function preset(kind: "today" | "yesterday" | "week" | "twoWeeks") {
  const current = today();
  activePreset.value = kind;
  if (kind === "today") {
    from.value = to.value = current;
  } else if (kind === "yesterday") {
    from.value = to.value = shiftDate(current, -1);
  } else {
    const currentDate = new Date(`${current}T00:00:00Z`);
    const daysSinceMonday = (currentDate.getUTCDay() + 6) % 7;
    from.value = shiftDate(current, -daysSinceMonday - (kind === "twoWeeks" ? 7 : 0));
    to.value = current;
  }
  load();
}
function customDate() {
  activePreset.value = undefined;
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
        <button class="quick-date" :class="{active:activePreset==='today'}" @click="preset('today')">今天</button
        ><button class="quick-date" :class="{active:activePreset==='yesterday'}" @click="preset('yesterday')">昨天</button
        ><button class="quick-date" :class="{active:activePreset==='week'}" @click="preset('week')">本周</button
        ><button class="quick-date" :class="{active:activePreset==='twoWeeks'}" @click="preset('twoWeeks')">两周</button>
      </div>
      <div class="field">
        <label>方向</label>
        <div class="direction-choice" role="group" aria-label="流转方向筛选">
          <button type="button" :class="{active:direction==='WAREHOUSE_TO_PRODUCTION'}" :aria-pressed="direction==='WAREHOUSE_TO_PRODUCTION'" @click="selectDirection('WAREHOUSE_TO_PRODUCTION')">仓库 → 生产车间</button>
          <button type="button" class="return" :class="{active:direction==='PRODUCTION_TO_WAREHOUSE'}" :aria-pressed="direction==='PRODUCTION_TO_WAREHOUSE'" @click="selectDirection('PRODUCTION_TO_WAREHOUSE')">车间 → 仓库</button>
        </div>
      </div>
      <button class="more-toggle" type="button" :aria-expanded="moreFilters" aria-controls="advanced-filters" @click="moreFilters=!moreFilters">
        <span>更多筛选</span><span aria-hidden="true">{{moreFilters ? '收起' : '展开'}} {{moreFilters ? '⌃' : '⌄'}}</span>
      </button>
      <div v-show="moreFilters" id="advanced-filters" class="advanced-filters">
        <div class="field">
          <label>搜索</label
          ><input class="input" v-model.trim="q" placeholder="产品、编码、批次或记录编号" @keyup.enter="load" />
        </div>
        <div class="field">
          <label>开始日期</label
          ><input class="input" type="date" v-model="from" :max="today()" @change="customDate" />
          <small class="hint">自定义日期查询至今天</small>
        </div>
      </div>
    </section>
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
            <div class="stats-total"><strong>共 {{ formatNumber(group.products.length) }} 种物料</strong><span>·</span><strong>{{ formatNumber(group.totalCartons) }} 箱</strong><span v-if="group.unknownItemCount">· 含 {{ group.unknownItemCount }} 项数量不确定</span></div>
            <div>
              <button v-for="product in group.products" :key="product.productName" type="button" class="product-stat" :aria-label="`查看 ${product.productName} 的原始流转记录`" @click="openProduct(product)">
                <strong>{{ product.productName }}<small v-if="product.unknownItemCount">（{{ product.unknownItemCount }} 项不确定）</small></strong>
                <span>{{ formatNumber(product.fullCartons) }} 箱</span>
                <span>{{ formatNumber(product.totalQuantity) }} 个</span>
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
      <div v-else class="stack">
        <MovementCard v-for="row in rows" :key="row.id" :movement="row" show-date />
      </div>
    </section>
    <nav class="quick-links card" aria-label="管理与导出"><RouterLink to="/reports">导出 PDF</RouterLink><RouterLink v-if="authenticated" to="/products">产品管理</RouterLink><RouterLink v-if="authenticated" to="/persons">人员管理</RouterLink></nav>
  </div>
    <dialog ref="recordDialog" class="record-dialog" aria-labelledby="record-dialog-title" @click="event => { if (event.target === recordDialog) recordDialog?.close(); }">
      <div class="dialog-heading"><h2 id="record-dialog-title">选择流转记录</h2><button type="button" class="btn" autofocus @click="recordDialog?.close()">关闭</button></div>
      <p class="dialog-summary">{{ selectedProduct }} · 共 {{ sourceMovements.length }} 个流转单</p>
      <div class="source-list">
        <button v-for="movement in sourceMovements" :key="movement.id" type="button" class="source-record" @click="openMovement(movement.id)">
          <strong>{{ movement.recordNo }} <span aria-hidden="true">›</span></strong>
          <span>{{ showDateTime(movement.movementTime) }} · {{ directionLabel(movement.direction) }}</span>
          <span>本单该物料：{{ formatNumber(movement.fullCartons) }} 箱 · {{ movement.unknownItemCount ? '已知数量 ' : '' }}{{ formatNumber(movement.totalQuantity) }} 个<span v-if="movement.unknownItemCount">（含 {{ movement.unknownItemCount }} 项数量不确定）</span></span>
          <span>{{ movement.senderName }} → {{ movement.receiverName }}</span>
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
.product-stat { display: grid; grid-template-columns: minmax(0, 1fr) auto auto auto; gap: 16px; padding: 12px 4px; border: 0; border-top: 1px solid #edf0f1; font: inherit; font-size: 14px; width: 100%; text-align: left; background: transparent; color: inherit; cursor: pointer; align-items: center; }
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
.source-record strong { display: flex; justify-content: space-between; color: #174a68; }
.source-record > span { font-size: 13px; }
.product-stat strong { overflow-wrap: anywhere; }
.product-stat span { color: #52626b; white-space: nowrap; font-variant-numeric: tabular-nums; }
@media (max-width: 560px) {
  .direction-choice button { font-size: 11px; }
  .stats-heading { align-items: flex-start; flex-direction: column; gap: 4px; }
  .product-stat { gap: 8px; font-size: 13px; }
}
</style>
