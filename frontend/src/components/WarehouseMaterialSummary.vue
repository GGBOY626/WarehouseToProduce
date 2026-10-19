<script setup lang="ts">
import { ref } from 'vue';
import { amountLabel, type MaterialSummary } from '../utils/warehouseSummary';
defineProps<{ materials: MaterialSummary[] }>();
const expanded = ref(new Set<string>());
function toggle(key: string) { if (expanded.value.has(key)) expanded.value.delete(key); else expanded.value.add(key); }
</script>
<template>
  <div class="material-summary">
    <article v-for="material in materials" :key="material.key" class="card summary-card">
      <header><div><h3>{{ material.name }} <small>（{{ material.batches.filter(b=>!b.missing).length }} 个批次<span v-if="material.batches.some(b=>b.missing)">，另有批号未录入</span>）</small></h3><p v-if="material.code||material.productId">{{material.code ? `物料编码 ${material.code}` : `产品 #${material.productId}`}}</p></div><div class="total"><span>{{Object.keys(material.quantities).length?'已确认：':''}}{{amountLabel(material)}}</span><span v-if="material.pending">另有 {{material.pending}} 项待盘点</span></div></header>
      <details v-for="batch in (expanded.has(material.key)?material.batches:material.batches.slice(0,3))" :key="batch.key" class="batch">
        <summary><span class="batch-name">{{batch.label}}<small v-if="batch.missing"> · {{batch.locations[0]?.pallets.join('、')}}</small></span><span class="quantity">{{amountLabel(batch)}}<small v-if="batch.pending">另有 {{batch.pending}} 项待盘点</small></span></summary>
        <ul><li v-for="location in batch.locations" :key="location.key"><div><strong>{{location.location}} <small v-if="location.finished" class="finished-tag">成品</small></strong><small>托盘 {{location.pallets.join('、')}}<template v-if="location.rawBatches.length"> · 原料批号 {{location.rawBatches.join('、')}}</template></small></div><span class="quantity">{{amountLabel(location)}}<small v-if="location.pending">另有 {{location.pending}} 项待盘点</small></span></li></ul>
      </details>
      <button v-if="material.batches.length>3" class="expand-batches" :aria-expanded="expanded.has(material.key)" @click="toggle(material.key)">{{expanded.has(material.key)?'收起剩余批次':`展开剩余批次（${material.batches.length-3}）`}}</button>
    </article>
  </div>
</template>
<style scoped>
.material-summary{display:grid;gap:12px}.summary-card{padding:12px 16px;min-width:0;content-visibility:auto;contain-intrinsic-size:auto 180px}.summary-card header{display:flex;justify-content:space-between;gap:12px;padding-bottom:8px}.summary-card h3{margin:0;font-size:15px;overflow-wrap:anywhere}.summary-card small,.summary-card p,.total{font-size:12px;font-weight:400;color:var(--muted)}.summary-card p{margin:4px 0 0}.total{display:grid;gap:4px;text-align:right}.batch{border-top:1px solid var(--line);font-size:13px}.batch summary{display:flex;align-items:center;gap:8px;min-height:44px;cursor:pointer;padding:8px 0;list-style:none}.batch summary::-webkit-details-marker{display:none}.batch summary::before{content:'›';color:var(--blue);font-size:20px}.batch[open] summary::before{content:'⌄'}.batch summary:hover{background:var(--soft-blue)}.batch-name{flex:1;min-width:0;overflow-wrap:anywhere}.quantity{text-align:right;font-variant-numeric:tabular-nums;overflow-wrap:anywhere;max-width:55%;color:var(--blue)}.quantity small{display:block;margin-top:3px}.batch ul{list-style:none;padding:0 12px;margin:0 0 8px;background:#f5f8fa;border-radius:6px}.batch li{display:flex;justify-content:space-between;gap:12px;padding:10px 0}.batch li+li{border-top:1px solid var(--line)}.batch li>div{min-width:0;overflow-wrap:anywhere}.batch li strong{font-size:12px;font-weight:500}.batch li small{display:block;margin-top:4px}.expand-batches{border:0;background:none;color:var(--blue);min-height:44px;cursor:pointer}.expand-batches:hover{text-decoration:underline}.batch summary:focus-visible,.expand-batches:focus-visible{outline:2px solid var(--blue);outline-offset:2px}@media(max-width:650px){.summary-card{padding:10px 12px}.summary-card header{flex-direction:column;gap:6px}.total{text-align:left}.summary-card h3 small{font-size:11px}.batch ul{padding:0 8px}}
</style>
<style scoped>.finished-tag{display:inline-block;padding:1px 5px;border-radius:4px;background:#e7f4ed;color:#216b45!important;font-size:10px;font-weight:700;vertical-align:middle}</style>
