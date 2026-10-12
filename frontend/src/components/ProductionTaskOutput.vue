<script setup lang="ts">
import type { ProductionTaskOutput } from "../types";
defineProps<{ output: ProductionTaskOutput }>();
</script>
<template>
  <div class="output-summary">
    <div class="output-heading"><strong>{{ output.productName }}</strong><b>{{ output.progressPercent }}% <small>生产完成率</small></b></div>
    <small>物料编码 {{ output.materialCode }}<template v-if="output.materialBatch"> · 物料批次 {{ output.materialBatch }}</template></small>
    <div class="output-values"><span>{{ output.unknownQuantityCount ? '已知回仓' : '累计回仓' }} <b>{{ output.receivedQuantity.toLocaleString() }}</b> / 计划 {{ output.targetQuantity.toLocaleString() }} {{ output.baseUnit }}</span><span v-if="output.receivedQuantity > output.targetQuantity">超计划 {{ (output.receivedQuantity - output.targetQuantity).toLocaleString() }} {{ output.baseUnit }}</span></div>
    <div class="progress" role="progressbar" aria-label="生产完成率" :aria-valuenow="output.progressPercent" :aria-valuemin="0" :aria-valuemax="100"><i :style="{ width: `${output.progressPercent}%` }" /></div>
    <p v-if="output.unknownQuantityCount" class="unknown">另有 {{ output.unknownQuantityCount }} 条回仓明细数量不确定，未计入完成率及预计耗用。</p>
  </div>
</template>
<style scoped>
.output-summary{display:grid;gap:10px;min-width:0}.output-heading,.output-values{display:flex;justify-content:space-between;align-items:baseline;gap:8px;flex-wrap:wrap}.output-heading strong{overflow-wrap:anywhere}.output-heading>b{color:#267566;font-size:21px}.output-heading small{font-size:12px;font-weight:400}.output-values{font-size:14px;color:#52626b}.progress{height:5px;background:#e5ecee;border-radius:3px;overflow:hidden}.progress i{display:block;height:100%;background:#267566}.unknown{margin:0;color:#9b3d17;font-size:13px}
</style>
