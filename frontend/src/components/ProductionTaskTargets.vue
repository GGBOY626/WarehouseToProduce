<script setup lang="ts">
import type { ProductionTaskTarget } from "../types";
defineProps<{ targets: ProductionTaskTarget[] }>();
</script>
<template>
  <div class="target-list">
    <div v-for="target in targets" :key="target.productId" class="target-summary">
      <div class="target-title"><strong>{{ target.productName }}</strong><small>{{ target.materialCode }}</small><span>{{ target.progressPercent }}%</span></div>
      <div class="target-values"><span>已入库 / 目标</span><b>{{ target.completedQuantity.toLocaleString() }} / {{ target.targetQuantity.toLocaleString() }} {{ target.baseUnit || '个' }}</b></div>
      <div class="progress" role="progressbar" :aria-label="`${target.productName}完成进度`" :aria-valuenow="target.progressPercent" :aria-valuemin="0" :aria-valuemax="100"><i :style="{ width: `${target.progressPercent}%` }" /></div>
    </div>
  </div>
</template>
<style scoped>
.target-list{display:grid;gap:14px;min-width:0}.target-summary{display:grid;gap:7px;min-width:0}.target-title{display:flex;gap:8px;align-items:baseline;flex-wrap:wrap}.target-title strong{overflow-wrap:anywhere}.target-title small{color:#66747c;overflow-wrap:anywhere}.target-title span{margin-left:auto;color:#267566;font-weight:700}.target-values{display:flex;flex-wrap:wrap;justify-content:space-between;gap:6px;font-size:13px}.target-values span{color:#66747c}.target-values b{font-variant-numeric:tabular-nums}.progress{height:4px;background:#e5ecee;border-radius:3px;overflow:hidden}.progress i{display:block;height:100%;background:#267566}
</style>
