<script setup lang="ts">
import type { ProductionTaskTarget } from "../types";
defineProps<{ targets: ProductionTaskTarget[] }>();
</script>
<template>
  <div class="target-list">
    <div v-for="target in targets" :key="target.productId" class="target-summary">
      <div class="target-title"><strong>{{ target.productName }}</strong><small>{{ target.materialCode }}</small></div>
      <p v-if="!target.materialTarget" class="shortage">此目标属于旧版成品入库产品，请编辑任务，改选仓库 → 生产车间的物料。</p>
      <template v-else>
        <div class="target-values"><span>净发料 / 所需</span><b>{{ target.completedQuantity.toLocaleString() }} / {{ target.targetQuantity.toLocaleString() }} {{ target.baseUnit || '个' }}</b></div>
        <small class="quantities">已发 {{ target.issuedQuantity.toLocaleString() }} · 已退 {{ target.returnedQuantity.toLocaleString() }} {{ target.baseUnit || '个' }}</small>
        <p v-if="target.shortageQuantity > 0" class="shortage">{{ target.unknownQuantityCount ? '按已知数量还缺' : '还缺' }} {{ target.shortageQuantity.toLocaleString() }} {{ target.baseUnit || '个' }}</p>
        <p v-else-if="!target.unknownQuantityCount" class="ready">已备齐</p>
        <p v-if="target.unknownQuantityCount" class="shortage">有 {{ target.unknownQuantityCount }} 条发料或退料明细数量不确定，请核对后确认是否备齐。</p>
        <div class="progress" role="progressbar" :aria-label="`${target.productName}备料进度`" :aria-valuenow="target.progressPercent" :aria-valuemin="0" :aria-valuemax="100"><i :style="{ width: `${target.progressPercent}%` }" /></div>
      </template>
    </div>
  </div>
</template>
<style scoped>
.shortage,.ready{margin:0;font-size:14px;font-weight:700}.shortage{color:#9b3d17}.ready{color:#267566}.quantities{color:#66747c}
.target-list{display:grid;gap:14px;min-width:0}.target-summary{display:grid;gap:7px;min-width:0}.target-title{display:flex;gap:8px;align-items:baseline;flex-wrap:wrap}.target-title strong{overflow-wrap:anywhere}.target-title small{color:#66747c;overflow-wrap:anywhere}.target-title span{margin-left:auto;color:#267566;font-weight:700}.target-values{display:flex;flex-wrap:wrap;justify-content:space-between;gap:6px;font-size:13px}.target-values span{color:#66747c}.target-values b{font-variant-numeric:tabular-nums}.progress{height:4px;background:#e5ecee;border-radius:3px;overflow:hidden}.progress i{display:block;height:100%;background:#267566}
</style>
