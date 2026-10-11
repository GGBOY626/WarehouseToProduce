<script setup lang="ts">
import type { Packaging } from '../types';
defineProps<{ packaging: Packaging[]; baseUnit: string }>();
</script>
<template>
  <div v-if="packaging.length" class="packing-breakdown">
    <span v-for="(spec,index) in packaging" :key="index">{{ spec.unitsPerCarton == null ? '规格未记录' : `${spec.unitsPerCarton.toLocaleString()} ${baseUnit}/箱` }} × <b>{{ spec.cartons.toLocaleString() }} {{ spec.type === 'TAIL' ? '尾箱' : '满箱' }}</b></span>
  </div>
</template>
<style scoped>
.packing-breakdown{display:flex;flex-wrap:wrap;gap:3px 14px;font-size:12px;color:var(--muted);line-height:1.5;overflow-wrap:anywhere}.packing-breakdown b{font-weight:600;color:var(--ink)}
</style>
