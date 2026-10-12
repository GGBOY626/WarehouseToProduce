<script setup lang="ts">
import type { MovementSummary } from '../types';
import { directionLabel } from '../utils/labels';
import { showDateTime, showTime } from '../utils/time';
import MaterialSummary from './MaterialSummary.vue';
withDefaults(defineProps<{ movement: MovementSummary; showDate?: boolean }>(), { showDate: false });
</script>
<template>
  <RouterLink :to="`/movements/${movement.id}`" class="movement-card card">
    <div class="movement-items">
      <MaterialSummary v-for="item in movement.items" :key="item.id" v-bind="item" :show-material-code="movement.direction === 'PRODUCTION_TO_WAREHOUSE' && !movement.returnMovement" :show-material-batch="true" />
    </div>
    <div class="record-footer">
      <time :datetime="movement.movementTime">{{ showDate ? showDateTime(movement.movementTime) : showTime(movement.movementTime) }}</time>
      <span :class="['movement-direction', {return:movement.direction==='PRODUCTION_TO_WAREHOUSE'}]">{{ directionLabel(movement.direction) }}<span v-if="movement.returnMovement"> · 退回</span></span>
      <span v-if="movement.photoCount" class="photo-tag">照片 {{ movement.photoCount }}</span>
      <span v-if="movement.status==='VOID'" class="status neutral">已作废</span>
      <span v-if="movement.hasIssues" class="status warn">有异常</span>
    </div>
  </RouterLink>
</template>
<style scoped>
.movement-card{display:block;padding:12px 14px}.movement-items{display:grid;gap:10px}.movement-items>:not(:first-child){border-top:1px solid var(--line);padding-top:10px}
.record-footer{display:flex;flex-wrap:wrap;gap:5px 12px;align-items:center;margin-top:9px;font-size:12px;color:var(--muted)}
.movement-direction{color:var(--blue)}.movement-direction.return{color:var(--green)}.photo-tag{padding:2px 6px;background:#edf0f2;border-radius:5px;font-size:11px}
.record-footer .status{min-height:20px;font-size:11px;padding:2px 6px}
</style>
