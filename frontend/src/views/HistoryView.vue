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
  hasIssue = ref<boolean | undefined>();
async function load() {
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
    rows.value = page.content;
    stats.value = currentStats;
    appliedFrom.value = from.value;
    appliedTo.value = to.value;
  } finally {
    loading.value = false;
  }
}
function shiftDate(date: string, days: number) {
  const [year, month, day] = date.split("-").map(Number);
  const value = new Date(Date.UTC(year, month - 1, day));
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}
function preset(kind: "today" | "yesterday" | "week" | "twoWeeks") {
  const current = today();
  if (kind === "today") {
    from.value = to.value = current;
  } else if (kind === "yesterday") {
    from.value = to.value = shiftDate(current, -1);
  } else {
    const currentDate = new Date(`${current}T00:00:00Z`);
    const daysSinceMonday = (currentDate.getUTCDay() + 6) % 7;
    from.value = shiftDate(current, kind === "week" ? -daysSinceMonday : -(daysSinceMonday + 7));
    to.value = current;
  }
  load();
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
        <button class="btn btn-secondary" @click="preset('today')">今天</button
        ><button class="btn btn-secondary" @click="preset('yesterday')">昨天</button
        ><button class="btn btn-secondary" @click="preset('week')">本周</button
        ><button class="btn btn-secondary" @click="preset('twoWeeks')">这两周</button>
      </div>
      <div class="grid-2">
        <div class="field">
          <label>开始日期</label
          ><input class="input" type="date" v-model="from" />
        </div>
        <div class="field">
          <label>结束日期</label
          ><input class="input" type="date" v-model="to" />
        </div>
      </div>
      <div class="field">
        <label>搜索</label
        ><input
          class="input"
          v-model.trim="q"
          placeholder="产品、物料编码、物料批次、记录编号"
          @keyup.enter="load"
        />
      </div>
      <div class="grid-2">
        <div class="field">
          <label>方向</label
          ><select class="select" v-model="direction">
            <option value="">全部方向</option>
            <option value="WAREHOUSE_TO_PRODUCTION">仓库 → 生产车间</option>
            <option value="PRODUCTION_TO_WAREHOUSE">生产车间 → 仓库</option>
          </select>
        </div>
        <div class="field">
          <label>状态</label
          ><select class="select" v-model="status">
            <option value="ACTIVE">正常记录</option>
            <option value="VOID">已作废</option>
          </select>
        </div>
      </div>
      <div class="grid-2">
        <label class="check"
          ><input
            type="checkbox"
            v-model="missingPhoto"
            :true-value="true"
            :false-value="undefined"
          />只看缺少照片</label
        ><label class="check"
          ><input
            type="checkbox"
            v-model="hasIssue"
            :true-value="true"
            :false-value="undefined"
          />只看有异常</label
        >
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
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.preset .btn {
  min-height: 42px;
}
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
  .stats-heading { align-items: flex-start; flex-direction: column; gap: 4px; }
  .product-stat { gap: 8px; font-size: 13px; }
}
</style>
