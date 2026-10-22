<script setup lang="ts">
import { computed } from 'vue';
import type { ProductionTaskMovement } from '../types';
import { showDateTime } from '../utils/time';
import PackagingBreakdown from './PackagingBreakdown.vue';
const props = defineProps<{ movements: ProductionTaskMovement[]; productId: number; productName: string; finished: boolean }>();
const rows = computed(() => props.movements
  .filter(m => props.finished ? m.direction === 'PRODUCTION_TO_WAREHOUSE' && !m.returnMovement : m.direction === 'WAREHOUSE_TO_PRODUCTION' || m.returnMovement)
  .map(m => ({ ...m, items: m.items.filter(i => i.productId === props.productId) }))
  .filter(m => m.items.length).sort((a,b) => a.movementTime.localeCompare(b.movementTime)));
const activeCount = computed(() => rows.value.filter(m => m.status === 'ACTIVE' && !m.returnMovement).length);
</script>
<template>
  <div class="history">
    <p class="history-lead">{{ productName }}{{ finished ? '（成品）' : '' }} · {{ finished ? '分批回仓' : '分次送入' }} {{ activeCount }} 次<span>按流转时间排列，退料单独标注；作废记录不计入进度。</span></p>
    <p v-if="!rows.length" class="empty">暂无关联流转记录。</p>
    <RouterLink v-for="m in rows" :key="m.id" :to="`/movements/${m.id}`" class="history-record" :class="{ voided: m.status !== 'ACTIVE' }">
      <div class="record-head"><strong>{{ showDateTime(m.movementTime) }}</strong><span>{{ m.direction === 'WAREHOUSE_TO_PRODUCTION' ? '仓库 → 车间' : '车间 → 仓库' }}{{ m.returnMovement ? ' · 退料' : finished ? ' · 成品回仓' : '' }}</span></div>
      <div v-for="(item,index) in m.items" :key="index" class="record-item">
        <div class="quantity"><b>{{ item.totalCartons.toLocaleString() }} 箱</b><b v-if="finished && item.totalBoxes != null">{{ item.totalBoxes.toLocaleString() }} 盒</b><b>{{ item.unknownQuantityCount ? '已知 ' : '' }}{{ item.totalQuantity.toLocaleString() }} {{ item.baseUnit || '个' }}</b></div>
        <PackagingBreakdown :packaging="item.packaging" :base-unit="item.baseUnit || '个'" />
        <small v-if="item.looseUnits">另有散装 {{ item.looseUnits.toLocaleString() }} {{ item.baseUnit }}</small>
        <small v-if="item.materialBatch || item.subBatch">批次 {{ item.materialBatch || '未记录' }}<template v-if="item.subBatch"> · 子批次 {{ item.subBatch }}</template></small>
        <small v-if="item.unknownQuantityCount" class="warning">{{ item.unknownQuantityCount }} 项数量未确定，已知数量不代表总量</small>
      </div>
      <div class="record-foot"><span>{{ m.recordNo }}<b v-if="m.status !== 'ACTIVE'"> · 已作废</b></span><span>查看流转单详情 ›</span></div>
    </RouterLink>
  </div>
</template>
<style scoped>
.history{display:grid;gap:10px}.history-lead{margin:0 0 4px;font-size:14px;font-weight:650}.history-lead span{display:block;margin-top:5px;font-size:12px;font-weight:400;color:var(--muted)}.history-record{display:grid;gap:10px;border:1px solid var(--line);border-radius:9px;padding:14px;background:#fff}.history-record:focus-visible{outline:2px solid var(--blue);outline-offset:3px}.record-head,.record-foot,.quantity{display:flex;justify-content:space-between;gap:8px;flex-wrap:wrap}.record-head{font-size:13px}.record-head>span{color:var(--green)}.record-item{display:grid;gap:5px;border-top:1px solid #edf0f2;padding-top:10px}.quantity{justify-content:flex-start;gap:18px;font-variant-numeric:tabular-nums;font-size:17px}.record-item small,.record-foot{font-size:12px;color:var(--muted)}.record-foot{padding-top:8px}.record-foot>span:last-child{color:var(--blue)}.record-item .warning{color:var(--orange)}.voided{background:#f4f5f6}.voided .record-foot b{color:var(--red)}
</style>
