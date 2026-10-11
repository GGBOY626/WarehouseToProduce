<script setup lang="ts">
defineOptions({ inheritAttrs: false });
defineProps<{
  productName: string;
  materialCode: string;
  materialBatch?: string | null;
  baseUnit: string;
  fullCartons: number;
  looseUnits: number;
  totalQuantity?: number | null;
  unknownItemCount?: number;
  quantityUnknown?: boolean;
}>();
const number = (value: number) => value.toLocaleString('zh-CN');
</script>
<template>
  <div class="material-summary">
    <div class="identity">
      <strong class="material-name">{{ productName }}</strong>
      <div class="material-identifiers">
        <span>编码 <b class="mono">{{ materialCode }}</b></span>
        <span>批次 <b class="mono">{{ materialBatch || '未记录' }}</b></span>
      </div>
    </div>
    <div class="material-quantities">
      <div><small>箱数</small><b>{{ number(fullCartons) }} <small>箱</small></b></div>
      <div><small>散装</small><b>{{ quantityUnknown ? '未知' : number(looseUnits) }} <small v-if="!quantityUnknown">{{ baseUnit }}</small></b></div>
      <div class="quantity-total"><small>{{ unknownItemCount ? '已知总数量' : '总数量' }}</small><b>{{ totalQuantity == null ? '未知' : number(totalQuantity) }} <small v-if="totalQuantity != null">{{ baseUnit }}</small></b></div>
    </div>
    <small v-if="quantityUnknown || unknownItemCount" class="unknown-note">{{ unknownItemCount ? `含 ${unknownItemCount} 项数量不确定` : '数量不确定' }}</small>
  </div>
</template>
<style scoped>
.material-summary{display:grid;grid-template-columns:minmax(0,1fr) auto;gap:6px 20px;align-items:center;min-width:0;width:100%}
.identity{min-width:0}.material-name{display:block;font-size:16px;line-height:1.4;font-weight:760;overflow-wrap:anywhere;color:var(--ink)}
.material-identifiers{display:flex;flex-wrap:wrap;gap:3px 14px;margin-top:4px;font-size:12px;color:var(--muted)}
.material-identifiers span{overflow-wrap:anywhere;min-width:0}.material-identifiers b{color:var(--blue);font-weight:600}
.material-quantities{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;font-variant-numeric:tabular-nums}
.material-quantities div{display:grid;gap:3px;min-width:0}.material-quantities small{color:var(--muted);font-size:11px;font-weight:400}
.material-quantities b{font-size:16px;overflow-wrap:anywhere;line-height:1.35}.quantity-total b{color:var(--blue)}
.unknown-note{grid-column:1/-1;color:var(--orange);font-size:11px}
@media(max-width:560px){.material-summary{grid-template-columns:minmax(0,1fr);gap:8px}.material-quantities{gap:8px}.material-name{font-size:15px}}
</style>
