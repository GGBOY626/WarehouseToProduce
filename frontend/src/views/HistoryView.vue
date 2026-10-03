<script setup lang="ts">
import { onMounted, ref } from "vue";
import MovementCard from "../components/MovementCard.vue";
import { movementsApi } from "../api";
import type { Direction, MovementStatus, MovementSummary, QueryStats } from "../types";
import { today } from "../utils/time";
const rows = ref<MovementSummary[]>([]),
  stats = ref<QueryStats>(),
  appliedFrom = ref(today()),
  appliedTo = ref(today()),
  loading = ref(false),
  from = ref(today()),
  to = ref(today()),
  direction = ref<Direction | "">(""),
  status = ref<MovementStatus>("ACTIVE"),
  q = ref(""),
  missingPhoto = ref<boolean | undefined>(),
  hasIssue = ref<boolean | undefined>(),
  moreFilters = ref(false),
  activePreset = ref<"today" | "yesterday" | "week" | "month" | undefined>("today");
let requestId = 0;
async function load() {
  const currentRequest = ++requestId;
  loading.value = true;
  stats.value = undefined;
  try {
    const params = {
      from: from.value,
      to: to.value,
      direction: direction.value || undefined,
      status: status.value || undefined,
      missingPhoto: missingPhoto.value,
      hasIssue: hasIssue.value,
      q: q.value,
    };
    const [page, currentStats] = await Promise.all([
      movementsApi.list({ ...params, size: 100 }),
      movementsApi.queryStats(params),
    ]);
    if (currentRequest !== requestId) return;
    rows.value = page.content;
    stats.value = currentStats;
    appliedFrom.value = from.value;
    appliedTo.value = to.value;
  } finally {
    if (currentRequest === requestId) loading.value = false;
  }
}
function selectDirection(value: Direction | "") {
  if (direction.value === value) return;
  direction.value = value;
  void load();
}
function shiftDate(date: string, days: number) {
  const [year, month, day] = date.split("-").map(Number);
  const value = new Date(Date.UTC(year, month - 1, day));
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}
function preset(kind: "today" | "yesterday" | "week" | "month") {
  const current = today();
  activePreset.value = kind;
  if (kind === "today") {
    from.value = to.value = current;
  } else if (kind === "yesterday") {
    from.value = to.value = shiftDate(current, -1);
  } else if (kind === "month") {
    from.value = `${current.slice(0, 7)}-01`;
    to.value = current;
  } else {
    const currentDate = new Date(`${current}T00:00:00Z`);
    const daysSinceMonday = (currentDate.getUTCDay() + 6) % 7;
    from.value = shiftDate(current, -daysSinceMonday);
    to.value = current;
  }
  load();
}
function customDate() {
  activePreset.value = undefined;
}
onMounted(load);
const directionLabel = (value: Direction) => value === "WAREHOUSE_TO_PRODUCTION" ? "仓库 → 生产车间" : "生产车间 → 仓库";
const formatNumber = (value: number) => value.toLocaleString("zh-CN");
</script>
<template>
  <div class="page">
    <h1 class="page-title">历史记录</h1>
    <p class="page-lead">按实际流转时间查询。</p>
    <section class="filters card card-pad">
      <div class="preset">
        <button class="quick-date" :class="{active:activePreset==='today'}" @click="preset('today')">今天</button
        ><button class="quick-date" :class="{active:activePreset==='yesterday'}" @click="preset('yesterday')">昨天</button
        ><button class="quick-date" :class="{active:activePreset==='week'}" @click="preset('week')">本周</button
        ><button class="quick-date" :class="{active:activePreset==='month'}" @click="preset('month')">本月</button>
      </div>
      <div class="field">
        <label>方向</label>
        <div class="direction-choice" role="group" aria-label="流转方向筛选">
          <button type="button" :class="{active:direction===''}" :aria-pressed="direction===''" @click="selectDirection('')">全部方向</button>
          <button type="button" :class="{active:direction==='WAREHOUSE_TO_PRODUCTION'}" :aria-pressed="direction==='WAREHOUSE_TO_PRODUCTION'" @click="selectDirection('WAREHOUSE_TO_PRODUCTION')">仓库 → 车间</button>
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
        <div class="grid-2">
          <div class="field">
            <label>开始日期</label
            ><input class="input" type="date" v-model="from" @input="customDate" />
          </div>
          <div class="field">
            <label>结束日期</label
            ><input class="input" type="date" v-model="to" @input="customDate" />
          </div>
        </div>
        <div class="field">
          <label>状态</label
          ><select class="select" v-model="status">
            <option value="ACTIVE">正常记录</option>
            <option value="VOID">已作废</option>
          </select>
        </div>
        <div class="grid-2 option-filters">
          <label class="check"
            ><input type="checkbox" v-model="missingPhoto" :true-value="true" :false-value="undefined" />只看缺少照片</label
          ><label class="check"
            ><input type="checkbox" v-model="hasIssue" :true-value="true" :false-value="undefined" />只看有异常</label>
        </div>
      </div>
      <button class="btn btn-primary btn-block" @click="load">查询记录</button>
    </section>
    <section class="section">
      <div class="section-head">
        <h2 class="section-title">查询结果</h2>
        <span class="hint">{{ rows.length }} 条</span>
      </div>
      <div v-if="!loading" class="query-stats card">
        <div class="stats-heading">
          <strong>查询范围统计</strong>
          <span>{{ appliedFrom }} ～ {{ appliedTo }}</span>
        </div>
        <div v-if="!stats?.directions.length" class="stats-empty">当前查询范围暂无统计数据</div>
        <div v-else>
          <section v-for="group in stats.directions" :key="group.direction" class="direction-stat">
            <h3>{{ directionLabel(group.direction) }}</h3>
            <div class="stats-total">{{ group.unknownItemCount ? '已知合计：' : '总计：' }}<strong>{{ formatNumber(group.totalCartons) }} 箱</strong><span>·</span><strong>{{ formatNumber(group.totalQuantity) }} 个</strong><span v-if="group.unknownItemCount">· 含 {{ group.unknownItemCount }} 项数量不确定</span></div>
            <div>
              <div v-for="product in group.products" :key="product.productName" class="product-stat">
                <strong>{{ product.productName }}<small v-if="product.unknownItemCount">（{{ product.unknownItemCount }} 项不确定）</small></strong>
                <span>{{ formatNumber(product.fullCartons) }} 箱</span>
                <span>{{ formatNumber(product.totalQuantity) }} 个</span>
              </div>
            </div>
          </section>
        </div>
      </div>
      <div v-if="loading" class="card empty">正在查询…</div>
      <div v-else-if="!rows.length" class="card empty">
        没有符合条件的记录。
      </div>
      <div v-else class="stack">
        <MovementCard v-for="row in rows" :key="row.id" :movement="row" show-date />
      </div>
    </section>
  </div>
</template>
<style scoped>
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
.direction-choice { display: grid; grid-template-columns: repeat(3,1fr); gap: 4px; padding: 4px; border-radius: 11px; background: #e7edef; }
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
.option-filters { gap: 8px; }
.option-filters .check { padding: 0 10px; border: 1px solid #dce4e7; border-radius: 8px; background: #fff; }
.check {
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}
.check input {
  width: 18px;
  height: 18px;
  accent-color: #174a68;
}
.query-stats { margin-bottom: 14px; overflow: hidden; }
.stats-heading { display: flex; justify-content: space-between; gap: 12px; padding: 14px 16px; background: #f3f7f8; color: #174a68; }
.stats-heading span { color: #687982; font-size: 13px; }
.stats-empty { padding: 24px 16px; text-align: center; color: #66747c; }
.direction-stat { padding: 15px 16px; border-top: 1px solid #e2e8ea; }
.direction-stat:first-child { border-top: 0; }
.direction-stat h3 { margin: 0 0 8px; font-size: 15px; color: #243e4b; }
.stats-total { display: flex; flex-wrap: wrap; gap: 7px; align-items: center; margin-bottom: 7px; color: #52626b; }
.stats-total strong { color: #174a68; font-size: 17px; }
.product-stat { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; gap: 16px; padding: 9px 0; border-top: 1px solid #edf0f1; font-size: 14px; }
.product-stat strong { overflow-wrap: anywhere; }
.product-stat span { color: #52626b; white-space: nowrap; font-variant-numeric: tabular-nums; }
@media (max-width: 560px) {
  .direction-choice button { font-size: 11px; }
  .stats-heading { align-items: flex-start; flex-direction: column; gap: 4px; }
  .product-stat { gap: 8px; font-size: 13px; }
}
</style>
