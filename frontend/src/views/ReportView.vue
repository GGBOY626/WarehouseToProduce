<script setup lang="ts">
import { ref } from "vue";
import { today } from "../utils/time";
const from = ref(today()),
  to = ref(today()),
  message = ref("");
function shiftDate(date: string, days: number) {
  const [year, month, day] = date.split("-").map(Number);
  const value = new Date(Date.UTC(year, month - 1, day));
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}
function downloadRange(start: string, end: string) {
  window.location.href = `/api/reports/pdf?from=${start}&to=${end}`;
}
function downloadPreset(kind: "today" | "yesterday" | "week" | "twoWeeks") {
  const current = today();
  if (kind === "today") return downloadRange(current, current);
  if (kind === "yesterday") {
    const previous = shiftDate(current, -1);
    return downloadRange(previous, previous);
  }
  const currentDate = new Date(`${current}T00:00:00Z`);
  const daysSinceMonday = (currentDate.getUTCDay() + 6) % 7;
  const start = shiftDate(current, kind === "week" ? -daysSinceMonday : -(daysSinceMonday + 7));
  downloadRange(start, current);
}
function downloadCustom() {
  message.value = "";
  if (!from.value || !to.value) {
    message.value = "请选择开始日期和结束日期。";
    return;
  }
  if (from.value > to.value) {
    message.value = "开始日期不能晚于结束日期。";
    return;
  }
  downloadRange(from.value, to.value);
}
</script>
<template>
  <div class="page">
    <h1 class="page-title">导出 PDF</h1>
    <p class="page-lead">按实际流转日期导出，默认不包含作废记录和照片原图。</p>
    <section class="card card-pad stack">
      <div class="field-label">快捷导出</div>
      <div class="preset-grid">
        <button class="btn btn-secondary" @click="downloadPreset('today')">导出当天</button>
        <button class="btn btn-secondary" @click="downloadPreset('yesterday')">导出昨天</button>
        <button class="btn btn-secondary" @click="downloadPreset('week')">导出本周</button>
        <button class="btn btn-secondary" @click="downloadPreset('twoWeeks')">导出这两周</button>
      </div>
      <hr class="divider" />
      <div class="field-label">自选导出日期</div>
      <div class="grid-2">
        <div class="field">
          <label>开始日期</label
          ><input class="input" type="date" v-model="from" />
        </div>
        <div class="field">
          <label>结束日期</label
          ><input class="input" type="date" v-model="to" />
        </div>
      </div>
      <div v-if="message" class="error-text" role="alert">{{ message }}</div>
      <button class="btn btn-primary btn-block" @click="downloadCustom">
        按所选日期导出 PDF
      </button>
      <p class="hint">
        PDF
        包含记录编号、方向、发送人、接收人、产品、物料编码、物料批次、数量、异常、照片状态和备注。
      </p>
    </section>
  </div>
</template>
<style scoped>.preset-grid{display:grid;grid-template-columns:1fr 1fr;gap:9px}.divider{margin:3px 0}.error-text{background:#fdeced;border-radius:8px;padding:10px 12px}@media(max-width:380px){.preset-grid{grid-template-columns:1fr}}</style>
