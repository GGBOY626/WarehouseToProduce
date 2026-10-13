<script setup lang="ts">
import type { ProductionTaskOutput } from "../types";
defineProps<{ output: ProductionTaskOutput }>();
</script>
<template>
  <div class="output-summary">
    <div class="output-heading"><strong>{{ output.productName }}</strong><b>{{ output.quantityBasis ? (output.productionPercent == null ? '—' : `${output.productionPercent}%`) : `${output.progressPercent}%` }} <small>{{ output.quantityBasis ? '生产完成度' : '计划回仓进度' }}</small></b></div>
    <small>物料编码 {{ output.materialCode }}</small>
    <div v-if="output.boxesPerCarton" class="output-values"><span>包装：{{ output.boxesPerCarton }} 盒/箱 × {{ output.unitsPerBox }} {{ output.primaryMaterialUnit }}/盒</span></div>
    <div class="output-values"><span>{{ output.unknownQuantityCount ? '已知回仓' : '累计回仓' }} <b>{{ output.receivedQuantity.toLocaleString() }}</b> {{ output.baseUnit }}</span><span v-if="output.receivedBoxes != null">折合 {{ output.receivedBoxes.toLocaleString(undefined, { maximumFractionDigits: 3 }) }} 盒 · {{ output.producedQuantity?.toLocaleString() }} {{ output.primaryMaterialUnit }}</span></div>
    <template v-if="output.quantityBasis">
      <div class="output-values"><span>实际净发 {{ output.primaryMaterialName }}：{{ output.netIssuedQuantity?.toLocaleString() }} {{ output.primaryMaterialUnit }}</span><b>{{ output.provisional ? '暂估损耗率' : '损耗率' }}：{{ output.lossPercent == null ? '—' : `${output.lossPercent}%` }}</b></div>
      <div class="progress" role="progressbar" aria-label="生产完成度" :aria-valuenow="output.productionPercent ?? undefined" :aria-valuemin="0" :aria-valuemax="100"><i :style="{ width: `${output.productionPercent || 0}%` }" /></div>
      <p v-if="output.productionPercent == null" class="unknown">无有效净发料，或关联数量未确定，暂无法计算完成度与损耗率。</p>
      <p v-else-if="output.provisional" class="hint">暂估未转为成品的部分可能仍在车间生产；确认生产结束并补齐退料、回仓后再核对损耗。</p>
      <p v-if="(output.producedQuantity || 0) > (output.netIssuedQuantity || 0)" class="unknown">回仓折算数量超过净发主料，请核对发料、退料和计量口径。</p>
    </template>
    <p v-else class="hint">此旧任务未配置主料和包装换算，编辑后可按实际净发料计算生产完成度与损耗率。</p>
    <p v-if="output.unknownQuantityCount" class="unknown">另有 {{ output.unknownQuantityCount }} 条回仓明细数量不确定。</p>
  </div>
</template>
<style scoped>
.output-summary{display:grid;gap:10px;min-width:0}.output-heading,.output-values{display:flex;justify-content:space-between;align-items:baseline;gap:8px;flex-wrap:wrap}.output-heading strong{overflow-wrap:anywhere}.output-heading>b{color:#267566;font-size:21px}.output-heading small{font-size:12px;font-weight:400}.output-values{font-size:14px;color:#52626b}.progress{height:5px;background:#e5ecee;border-radius:3px;overflow:hidden}.progress i{display:block;height:100%;background:#267566}.unknown{margin:0;color:#9b3d17;font-size:13px}
</style>
