<script setup lang="ts">
import { computed } from 'vue';
import type { ProductionTask } from '../types';
import { formatExactCount, packingRemainder, palletSummary } from '../utils/receiptForecast';
const props = defineProps<{ task: ProductionTask; detailed?: boolean }>();
const forecast = computed(() => props.task.forecast);
const unit = computed(() => props.task.output?.quantityBasis === 'BOX' ? '盒' : props.task.output?.baseUnit || '个');
const unavailable = computed(() => !forecast.value || forecast.value.status === 'NEEDS_CONFIGURATION' ? '待配置' : '待核对');
const columns = computed(() => [
  { label: '预计生产', value: forecast.value?.expected },
  { label: forecast.value?.receivedIncomplete ? '已回库（已知）' : '已关联回库', value: forecast.value?.received },
  { label: forecast.value?.excess ? '超额回库' : '预计待回库', value: forecast.value?.excess || forecast.value?.remaining },
]);
const progress = computed(() => forecast.value?.progressPercent);
</script>
<template>
  <section class="receipt-summary" :class="{ detailed, excess: forecast?.excess }" :aria-labelledby="`receipt-title-${task.id}`">
    <header><component :is="detailed?'h2':'h4'" :id="`receipt-title-${task.id}`" class="receipt-title">预计成品回库</component><span>{{ task.output?.productName || '尚未关联成品' }}</span></header>
    <div class="receipt-metrics">
      <div v-for="(column,index) in columns" :key="index" class="receipt-metric" :class="{ pending: index===2 }">
        <span class="metric-label">{{column.label}}</span>
        <template v-if="column.value">
          <strong>{{formatExactCount(column.value.cartons)}} <small>箱</small></strong>
          <span v-if="packingRemainder(column.value,unit)" class="remainder">余 {{packingRemainder(column.value,unit)}}</span>
          <span v-if="column.value.pallets!=null" class="pallets">折合 {{palletSummary(column.value,unit)}}</span>
        </template>
        <strong v-else class="unavailable">{{unavailable}}</strong>
      </div>
    </div>
    <div class="receipt-progress"><span>成品回库进度</span><strong>{{progress==null?'—':`${progress}%`}}</strong><div class="receipt-track" role="progressbar" :aria-label="`${task.name}：成品回库进度`" :aria-valuemin="0" :aria-valuemax="100" :aria-valuenow="progress==null?undefined:Math.min(100,progress)" :aria-valuetext="progress==null?'暂无可计算数据':`${progress}%${forecast?.excess?'，超额回库':''}`"><i :style="{width:`${Math.max(0,Math.min(100,progress??0))}%`}" /></div></div>
    <p v-if="!forecast || forecast.status==='NEEDS_CONFIGURATION'" class="receipt-note">待配置成品、主料、箱规及产出配比。</p>
    <p v-else-if="forecast.status==='NEEDS_REVIEW'" class="receipt-warning">数量待核对，暂不显示完整待回库量及进度。</p>
    <p v-if="forecast?.excess" class="receipt-warning">{{forecast.receivedIncomplete?'已知回库已超额，另有数量待核对。':'已回库超过预计产量，请核对配比和关联记录。'}}</p>
    <div v-if="detailed" class="receipt-basis">
      <h3>计算依据</h3>
      <p v-if="task.output?.yieldMaterialQuantity">主料 {{task.output.primaryMaterialName}}（{{task.output.primaryMaterialUnit}}）：净发 {{task.output.netIssuedQuantity?.toLocaleString() ?? '—'}}；每投入 {{task.output.yieldMaterialQuantity.toLocaleString()}}，对应产出 {{task.output.yieldOutputQuantity?.toLocaleString()}} {{unit}}。</p>
      <p v-if="task.output?.boxesPerCarton">成品规格：{{task.output.boxesPerCarton}} 盒/箱 × {{task.output.unitsPerBox}} 内装数量/盒{{task.output.cartonsPerPallet ? `；${task.output.cartonsPerPallet} 箱/托盘` : ''}}。{{task.output.quantityBasis==='BOX'?'回库数量按盒登记。':'回库数量按成品基础单位登记。'}}</p>
      <p>预计产量 = 主料净发量 × 产出配比；净发量扣除退料。此为理论产量，未计损耗或其他物料瓶颈。已回库仅累计本任务关联、有效、非退回且产品身份匹配的成品入库。</p>
      <p>箱数和托盘为按任务成品规格折合的数量，并非流转单中尾箱的实占箱数。余盒、余件和配比产生的分数均保留，不四舍五入；回库百分比保留两位小数，超额不封顶。</p>
      <p v-if="forecast && !forecast.received">已知关联回库：{{formatExactCount(forecast.knownReceivedQuantity)}} {{unit}}，补充成品箱规后可折算。</p>
      <p v-for="note in forecast?.messages" :key="note" class="receipt-warning">{{note}}</p>
    </div>
  </section>
</template>
<style scoped>
.receipt-summary{margin-top:16px;padding:12px 14px;border:1px solid var(--line);border-radius:8px;background:#f7faf9;min-width:0}
header{display:flex;align-items:baseline;justify-content:space-between;gap:10px}.receipt-title{font-size:12px;margin:0;color:var(--green);font-weight:700;flex-shrink:0}header>span{font-size:11px;color:var(--muted);overflow-wrap:anywhere;text-align:right}
.receipt-metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin-top:10px}.receipt-metric{display:flex;flex-direction:column;align-items:flex-start;gap:3px;min-width:0;overflow-wrap:anywhere}.receipt-metric+.receipt-metric{border-left:1px solid var(--line);padding-left:12px}.metric-label{font-size:11px;color:var(--muted)}.receipt-metric strong{font-size:18px;font-variant-numeric:tabular-nums;line-height:1.35}.receipt-metric small{font-size:11px;font-weight:400}.pending strong{color:var(--green)}.excess .pending strong{color:var(--orange)}.receipt-metric .unavailable{color:var(--muted);font-size:14px}.remainder,.pallets{font-size:11px;line-height:1.5;font-variant-numeric:tabular-nums}.pallets{color:var(--muted)}
.receipt-progress{display:grid;grid-template-columns:auto auto minmax(30px,1fr);align-items:center;gap:10px;margin-top:10px;font-size:11px;color:var(--muted)}.receipt-progress strong{font-variant-numeric:tabular-nums;color:var(--green)}.receipt-track{height:6px;border-radius:4px;background:#e2eae7;overflow:hidden}.receipt-track i{display:block;height:100%;background:var(--green)}.receipt-note,.receipt-warning{font-size:11px;line-height:1.6;margin:7px 0 0;color:var(--muted)}.receipt-warning{color:var(--orange)}
.detailed{margin:0 0 18px;padding:18px;background:var(--panel)}.detailed .receipt-title{font-size:15px}.receipt-basis{border-top:1px solid var(--line);padding-top:12px;margin-top:12px;color:var(--muted);font-size:12px;line-height:1.7}.receipt-basis h3{margin:0;color:var(--ink)}.receipt-basis p{margin:6px 0 0}
@media(max-width:520px){.receipt-summary{padding:11px}.receipt-metrics{gap:7px}.receipt-metric+.receipt-metric{padding-left:7px}.receipt-metric strong{font-size:16px}.receipt-progress{gap:7px}.detailed{padding:14px}}
</style>
