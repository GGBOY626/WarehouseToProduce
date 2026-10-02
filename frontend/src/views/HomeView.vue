<script setup lang="ts">
import { onMounted, ref } from "vue";
import MovementCard from "../components/MovementCard.vue";
import { movementsApi } from "../api";
import type { Direction, MovementSummary, TodayStats } from "../types";
import { today } from "../utils/time";
import { useAuth } from "../auth";

const selectedDirection = ref<Direction>("WAREHOUSE_TO_PRODUCTION");
const rows = ref<MovementSummary[]>([]);
const stats = ref<TodayStats>();
const loading = ref(true);
let requestId = 0;
const { authenticated } = useAuth();

async function load(direction: Direction) {
  const currentRequest = ++requestId;
  loading.value = true;
  rows.value = [];
  stats.value = undefined;
  try {
    const date = today();
    const [first, currentStats] = await Promise.all([
      movementsApi.list({ from: date, to: date, direction, status: "ACTIVE", page: 0, size: 100 }),
      movementsApi.stats(date, direction),
    ]);
    const content = [...first.content];
    for (let page = 1; page < first.totalPages; page++) {
      if (currentRequest !== requestId) return;
      const next = await movementsApi.list({ from: date, to: date, direction, status: "ACTIVE", page, size: 100 });
      content.push(...next.content);
    }
    if (currentRequest === requestId) {
      rows.value = content;
      stats.value = currentStats;
    }
  } finally {
    if (currentRequest === requestId) loading.value = false;
  }
}

function selectDirection(direction: Direction) {
  if (direction === selectedDirection.value) return;
  selectedDirection.value = direction;
  void load(direction);
}

onMounted(() => load(selectedDirection.value));
const formatNumber = (value: number) => value.toLocaleString("zh-CN");
</script>

<template>
  <div class="page home">
    <section v-if="authenticated" class="intro"><p class="eyebrow">新西兰时间 · 今日流转</p><h1 class="page-title">现在要登记哪一次交接？</h1><p class="page-lead">点击上方方向直接新增记录，下方可独立切换今日记录方向。</p></section>
    <div v-if="authenticated" class="direction-actions">
      <RouterLink class="direction-button outbound" to="/movements/new/warehouse-to-production"><span class="place">仓库</span><span class="arrow">→</span><span class="place">生产车间</span></RouterLink>
      <RouterLink class="direction-button inbound" to="/movements/new/production-to-warehouse"><span class="place">生产车间</span><span class="arrow">→</span><span class="place">仓库</span></RouterLink>
    </div>
    <div class="record-filter">
      <span class="filter-label">今日记录方向</span>
      <div class="direction-toggle" role="group" aria-label="今日记录方向切换">
        <button type="button" :class="{active:selectedDirection==='WAREHOUSE_TO_PRODUCTION'}" :aria-pressed="selectedDirection==='WAREHOUSE_TO_PRODUCTION'" @click="selectDirection('WAREHOUSE_TO_PRODUCTION')">仓库 → 生产车间</button>
        <button type="button" class="return" :class="{active:selectedDirection==='PRODUCTION_TO_WAREHOUSE'}" :aria-pressed="selectedDirection==='PRODUCTION_TO_WAREHOUSE'" @click="selectDirection('PRODUCTION_TO_WAREHOUSE')">生产车间 → 仓库</button>
      </div>
    </div>
    <section class="section stats-section">
      <h2 class="section-title">今日统计</h2>
      <div class="card stats-card">
        <div v-if="loading" class="stats-empty">正在统计…</div>
        <div v-else-if="!stats?.products.length" class="stats-empty">今日暂无数据</div>
        <template v-else>
          <div class="stats-total">总计：<strong>{{formatNumber(stats.totalCartons)}} 箱</strong><span>·</span><strong>{{formatNumber(stats.totalQuantity)}} 个</strong></div>
          <div class="stats-products">
            <div v-for="product in stats.products" :key="product.productName" class="stats-row">
              <strong>{{product.productName}}</strong><span>{{formatNumber(product.fullCartons)}} 箱</span><span>{{formatNumber(product.totalQuantity)}} 个</span>
            </div>
          </div>
        </template>
      </div>
    </section>
    <section class="section"><div class="section-head"><h2 class="section-title">今日记录</h2><RouterLink to="/history" class="link">查看全部</RouterLink></div><div v-if="loading" class="card empty">正在读取记录…</div><div v-else-if="!rows.length" class="card empty">今天该方向还没有记录。</div><div v-else class="stack"><MovementCard v-for="row in rows" :key="row.id" :movement="row"/></div></section>
    <nav class="quick-links card"><RouterLink to="/history">历史记录</RouterLink><RouterLink to="/reports">导出 PDF</RouterLink><RouterLink to="/products">产品管理</RouterLink><RouterLink to="/persons">人员管理</RouterLink></nav>
  </div>
</template>

<style scoped>.intro{padding-top:6px}.eyebrow{color:#174a68;font-size:12px;font-weight:760;letter-spacing:.08em;margin:0 0 8px}.direction-actions{display:grid;grid-template-columns:1fr 1fr;gap:10px}.direction-button{min-height:88px;border-radius:12px;padding:15px;color:#fff;display:grid;grid-template-columns:1fr auto 1fr;align-items:center;gap:7px;box-shadow:0 5px 16px rgba(23,74,104,.16);text-decoration:none;transition:transform .15s,box-shadow .15s}.direction-button:hover{transform:translateY(-1px);box-shadow:0 9px 20px rgba(23,74,104,.22)}.direction-button:focus-visible{outline:4px solid rgba(31,107,138,.35);outline-offset:3px}.outbound{background:#174a68}.inbound{background:#267566}.place{font-size:18px;font-weight:800}.place:last-of-type{text-align:right}.arrow{text-align:center;font-size:25px}.record-filter{display:grid;gap:7px;margin-top:16px}.filter-label{font-size:13px;font-weight:760;color:#60737d}.direction-toggle{display:grid;grid-template-columns:1fr 1fr;gap:4px;padding:4px;background:#e3eaed;border-radius:11px}.direction-toggle button{min-height:44px;border:0;border-radius:8px;padding:8px;background:transparent;color:#60737d;font:inherit;font-size:14px;font-weight:760;cursor:pointer}.direction-toggle button.active{background:#fff;color:#174a68;box-shadow:0 2px 8px rgba(32,53,64,.16)}.direction-toggle button.return.active{color:#267566}.direction-toggle button:focus-visible{outline:3px solid rgba(31,107,138,.35);outline-offset:1px}.stats-section>.section-title{margin-bottom:10px}.stats-card{overflow:hidden}.stats-empty{padding:24px;text-align:center;color:#66747c}.stats-total{display:flex;align-items:center;flex-wrap:wrap;gap:7px;padding:15px 16px;background:#f3f7f8;color:#52626b}.stats-total strong{font-size:18px;color:#174a68}.stats-products{padding:0 16px}.stats-row{display:grid;grid-template-columns:minmax(0,1fr) auto auto;gap:16px;align-items:center;padding:11px 0;border-bottom:1px solid #e3e8ea;font-size:14px}.stats-row:last-child{border-bottom:0}.stats-row strong{overflow-wrap:anywhere}.stats-row span{color:#52626b;font-variant-numeric:tabular-nums;white-space:nowrap}.quick-links{margin-top:22px;display:grid;grid-template-columns:1fr 1fr}.quick-links a{min-height:52px;display:grid;place-items:center;font-weight:680;border-bottom:1px solid #dce2e5}.quick-links a:nth-child(odd){border-right:1px solid #dce2e5}.quick-links a:nth-last-child(-n+2){border-bottom:0}@media(max-width:560px){.direction-button{min-height:82px;padding:11px 8px}.place{font-size:14px}.arrow{font-size:20px}.direction-toggle button{padding:7px 4px;font-size:12px}.stats-row{gap:8px;font-size:13px}}</style>
