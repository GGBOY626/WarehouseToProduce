<script setup lang="ts">
import type { ProductionTaskTarget } from "../types";
defineProps<{ targets: ProductionTaskTarget[] }>();
</script>
<template>
  <div class="target-list">
    <div v-for="(target, index) in targets" :key="`${target.productId}:${target.materialBatch}:${index}`" class="target-summary">
      <div class="target-title"><strong>{{ target.productName }}</strong><small>物料编码 {{ target.materialCode }}</small><small v-if="target.materialBatch">物料批次 {{ target.materialBatch }}</small></div>
      <p v-if="!target.materialTarget" class="shortage">此目标属于旧版成品入库产品，请编辑任务，改选仓库 → 生产车间的物料。</p>
      <template v-else>
        <div class="target-values"><span>净发料 / 所需</span><b>{{ target.completedQuantity.toLocaleString() }} / {{ target.targetQuantity.toLocaleString() }} {{ target.baseUnit || '个' }}</b></div>
        <small class="quantities">已发 {{ target.issuedQuantity.toLocaleString() }} · 已退 {{ target.returnedQuantity.toLocaleString() }} {{ target.baseUnit || '个' }}</small>
        <small v-if="target.estimatedUsedQuantity != null" class="quantities">按回仓产量预计耗用 {{ target.estimatedUsedQuantity.toLocaleString(undefined, { maximumFractionDigits: 6 }) }} {{ target.baseUnit || '个' }}</small>
        <p v-if="target.shortageQuantity > 0" class="shortage">{{ target.unknownQuantityCount ? '按已知数量还缺' : '还缺' }} {{ target.shortageQuantity.toLocaleString() }} {{ target.baseUnit || '个' }}</p>
        <p v-else-if="!target.unknownQuantityCount" class="ready">已备齐</p>
        <p v-if="target.unknownQuantityCount" class="shortage">有 {{ target.unknownQuantityCount }} 条发料或退料明细数量不确定，请核对后确认是否备齐。</p>
        <details v-if="target.batches?.length" class="batch-breakdown" @click.stop>
          <summary>查看批次明细（{{ target.batches.length }} 项）</summary>
          <div v-for="(batch, index) in target.batches" :key="index" class="batch-row">
            <span>{{ batch.materialBatch || '历史记录' }}<template v-if="batch.subBatch"> · {{ batch.subBatch }}</template></span>
            <span>发 {{ batch.issuedQuantity.toLocaleString() }} · 退 {{ batch.returnedQuantity.toLocaleString() }} · 净 {{ batch.netQuantity.toLocaleString() }} {{ target.baseUnit }}</span>
            <small v-if="batch.unknownQuantityCount">另有 {{ batch.unknownQuantityCount }} 条数量不确定</small>
          </div>
        </details>
        <div class="progress" role="progressbar" :aria-label="`${target.productName}备料进度`" :aria-valuenow="target.progressPercent" :aria-valuemin="0" :aria-valuemax="100"><i :style="{ width: `${target.progressPercent}%` }" /></div>
      </template>
    </div>
  </div>
</template>
<style scoped>
.batch-breakdown{font-size:13px;color:#52626b}.batch-breakdown summary{cursor:pointer;padding:6px 0}.batch-row{display:flex;flex-wrap:wrap;justify-content:space-between;gap:6px;padding:6px 0;border-top:1px solid #e5ecee}
.shortage,.ready{margin:0;font-size:14px;font-weight:700}.shortage{color:#9b3d17}.ready{color:#267566}.quantities{color:#66747c}
.target-list{display:grid;gap:14px;min-width:0}.target-summary{display:grid;gap:7px;min-width:0}.target-title{display:flex;gap:8px;align-items:baseline;flex-wrap:wrap}.target-title strong{overflow-wrap:anywhere}.target-title small{color:#66747c;overflow-wrap:anywhere}.target-title span{margin-left:auto;color:#267566;font-weight:700}.target-values{display:flex;flex-wrap:wrap;justify-content:space-between;gap:6px;font-size:13px}.target-values span{color:#66747c}.target-values b{font-variant-numeric:tabular-nums}.progress{height:4px;background:#e5ecee;border-radius:3px;overflow:hidden}.progress i{display:block;height:100%;background:#267566}
</style>
