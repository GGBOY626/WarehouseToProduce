<script setup lang="ts">
import { onMounted, ref } from "vue";
import MovementCard from "../components/MovementCard.vue";
import { movementsApi } from "../api";
import type { Direction, MovementSummary } from "../types";
import { today } from "../utils/time";

const selectedDirection = ref<Direction>("WAREHOUSE_TO_PRODUCTION");
const rows = ref<MovementSummary[]>([]);
const loading = ref(true);
let requestId = 0;

async function load(direction: Direction) {
  const currentRequest = ++requestId;
  loading.value = true;
  rows.value = [];
  try {
    const result = await movementsApi.list({ from: today(), to: today(), direction, status: "ACTIVE", size: 8 });
    if (currentRequest === requestId) rows.value = result.content;
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
</script>

<template>
  <div class="page home">
    <section class="intro"><p class="eyebrow">新西兰时间 · 今日流转</p><h1 class="page-title">现在要登记哪一次交接？</h1><p class="page-lead">选择方向查看今日记录，或点击“新增登记”填写新的流转。</p></section>
    <div class="direction-actions">
      <div :class="['direction-button','outbound',{selected:selectedDirection==='WAREHOUSE_TO_PRODUCTION'}]" role="button" tabindex="0" :aria-pressed="selectedDirection==='WAREHOUSE_TO_PRODUCTION'" @click="selectDirection('WAREHOUSE_TO_PRODUCTION')" @keydown.enter="selectDirection('WAREHOUSE_TO_PRODUCTION')" @keydown.space.prevent="selectDirection('WAREHOUSE_TO_PRODUCTION')">
        <span class="place">仓库</span><span class="arrow">→</span><span class="place">生产车间</span><span v-if="selectedDirection==='WAREHOUSE_TO_PRODUCTION'" class="selected-mark">✓ 当前显示</span><RouterLink class="register-link" to="/movements/new/warehouse-to-production" @click.stop>新增登记</RouterLink>
      </div>
      <div :class="['direction-button','inbound',{selected:selectedDirection==='PRODUCTION_TO_WAREHOUSE'}]" role="button" tabindex="0" :aria-pressed="selectedDirection==='PRODUCTION_TO_WAREHOUSE'" @click="selectDirection('PRODUCTION_TO_WAREHOUSE')" @keydown.enter="selectDirection('PRODUCTION_TO_WAREHOUSE')" @keydown.space.prevent="selectDirection('PRODUCTION_TO_WAREHOUSE')">
        <span class="place">生产车间</span><span class="arrow">→</span><span class="place">仓库</span><span v-if="selectedDirection==='PRODUCTION_TO_WAREHOUSE'" class="selected-mark">✓ 当前显示</span><RouterLink class="register-link" to="/movements/new/production-to-warehouse" @click.stop>新增登记</RouterLink>
      </div>
    </div>
    <section class="section"><div class="section-head"><h2 class="section-title">今日记录 · {{selectedDirection==='WAREHOUSE_TO_PRODUCTION'?'仓库 → 生产车间':'生产车间 → 仓库'}}</h2><RouterLink to="/history" class="link">查看全部</RouterLink></div><div v-if="loading" class="card empty">正在读取记录…</div><div v-else-if="!rows.length" class="card empty">今天该方向还没有记录。</div><div v-else class="stack"><MovementCard v-for="row in rows" :key="row.id" :movement="row"/></div></section>
    <nav class="quick-links card"><RouterLink to="/history">历史记录</RouterLink><RouterLink to="/reports">导出 PDF</RouterLink><RouterLink to="/products">产品管理</RouterLink><RouterLink to="/persons">人员管理</RouterLink></nav>
  </div>
</template>

<style scoped>.intro{padding-top:6px}.eyebrow{color:#174a68;font-size:12px;font-weight:760;letter-spacing:.08em;margin:0 0 8px}.direction-actions{display:grid;gap:10px}.direction-button{min-height:104px;border:3px solid transparent;border-radius:12px;padding:15px;color:#fff;display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:10px;box-shadow:0 5px 16px rgba(23,74,104,.16);cursor:pointer;transition:transform .15s,box-shadow .15s,opacity .15s}.direction-button:not(.selected){opacity:.72;box-shadow:0 2px 8px rgba(23,74,104,.1)}.direction-button.selected{border-color:#fff;outline:3px solid currentColor;outline-offset:1px;transform:translateY(-1px)}.direction-button:focus-visible{outline:4px solid rgba(31,107,138,.35);outline-offset:3px}.outbound{background:#174a68}.inbound{background:#267566}.place{font-size:18px;font-weight:800}.arrow{text-align:center;font-size:25px}.selected-mark{grid-column:1/3;font-size:12px;font-weight:760}.register-link{grid-column:3;justify-self:end;background:rgba(255,255,255,.95);color:#174a68;border-radius:7px;padding:7px 11px;font-size:12px;font-weight:760}.selected-mark,.register-link{grid-row:2}.quick-links{margin-top:22px;display:grid;grid-template-columns:1fr 1fr}.quick-links a{min-height:52px;display:grid;place-items:center;font-weight:680;border-bottom:1px solid #dce2e5}.quick-links a:nth-child(odd){border-right:1px solid #dce2e5}.quick-links a:nth-last-child(-n+2){border-bottom:0}</style>
