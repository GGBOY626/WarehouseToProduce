<script setup lang="ts">
import { ref, watch } from "vue";
import { today } from "../utils/time";
import { movementsApi } from "../api";
import { errorMessage } from "../api/http";
import { dailyMovementRows, formatDailyMovementCopy } from "../utils/dailyMovementCopy";
import type { DailyMovementRow } from "../utils/dailyMovementCopy";
import type { Direction } from "../types";

type ExportDirection = "" | Direction;
const from = ref(today());
const to = ref(today());
const direction = ref<ExportDirection>("");
const message = ref("");
const copyDirection = ref<Direction>("WAREHOUSE_TO_PRODUCTION");
const copying = ref(false);
const copyMessage = ref("");
const copyFailed = ref(false);
const locationEditorOpen = ref(false);
const locationLoading = ref(false);
const locationError = ref("");
const locationRows = ref<DailyMovementRow[]>([]);
const locationValues = ref<Record<string, string>>({});
const editorDate = ref("");
const editorDirection = ref<Direction>("WAREHOUSE_TO_PRODUCTION");

function locationStorageKey(date: string, selectedDirection: Direction) {
  return `daily-movement-locations:${date}:${selectedDirection}`;
}
function readLocations(date: string, selectedDirection: Direction): Record<string, string> {
  try {
    const stored = JSON.parse(localStorage.getItem(locationStorageKey(date, selectedDirection)) || '{}');
    return stored && typeof stored === 'object' && !Array.isArray(stored) ? stored : {};
  } catch { return {}; }
}
function saveLocations() {
  const changes: Record<string, string> = {};
  for (const row of locationRows.value) {
    const value = locationValues.value[row.key]?.trim();
    if (value && value !== 'TT') changes[row.key] = value;
  }
  try {
    const key = locationStorageKey(editorDate.value, editorDirection.value);
    if (Object.keys(changes).length) localStorage.setItem(key, JSON.stringify(changes));
    else localStorage.removeItem(key);
    locationError.value = '';
  } catch { locationError.value = '浏览器无法保存库位；本次复制仍会使用当前填写的内容。'; }
}
async function toggleLocationEditor() {
  if (locationEditorOpen.value) { locationEditorOpen.value = false; return; }
  locationLoading.value = true;
  locationError.value = '';
  try {
    const date = today();
    const selectedDirection = copyDirection.value;
    const stats = await movementsApi.queryStats({ from: date, to: date, direction: selectedDirection, status: 'ACTIVE' });
    editorDate.value = date;
    editorDirection.value = selectedDirection;
    locationRows.value = dailyMovementRows(selectedDirection, stats);
    const saved = readLocations(date, selectedDirection);
    locationValues.value = Object.fromEntries(locationRows.value.map(row => [row.key, saved[row.key] || 'TT']));
    locationEditorOpen.value = true;
  } catch (cause) { locationError.value = errorMessage(cause); }
  finally { locationLoading.value = false; }
}
watch(copyDirection, () => { locationEditorOpen.value = false; locationError.value = ''; });

async function writeClipboard(value: string) {
  if (navigator.clipboard?.writeText) {
    try { await navigator.clipboard.writeText(value); return; } catch { /* Try the selection fallback. */ }
  }
  const input = document.createElement('textarea');
  input.value = value;
  input.style.position = 'fixed';
  input.style.left = '-9999px';
  document.body.appendChild(input);
  input.select();
  try {
    if (!document.execCommand('copy')) throw new Error('复制失败，请检查浏览器的剪贴板权限。');
  } finally { input.remove(); }
}

async function copyToday() {
  if (copying.value) return;
  copying.value = true;
  copyMessage.value = '';
  copyFailed.value = false;
  try {
    const date = today();
    const stats = await movementsApi.queryStats({ from: date, to: date, direction: copyDirection.value, status: 'ACTIVE' });
    const locations = locationEditorOpen.value && editorDate.value === date && editorDirection.value === copyDirection.value
      ? locationValues.value : readLocations(date, copyDirection.value);
    await writeClipboard(formatDailyMovementCopy(date, copyDirection.value, stats, locations));
    copyMessage.value = '已复制今日流转摘要。';
  } catch (cause) {
    copyFailed.value = true;
    const message = errorMessage(cause);
    copyMessage.value = message === '操作失败，请稍后重试。' && cause instanceof Error ? cause.message : message;
  } finally { copying.value = false; }
}

function shiftDate(date: string, days: number) {
  const [year, month, day] = date.split("-").map(Number);
  const value = new Date(Date.UTC(year, month - 1, day));
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}

function downloadRange(start: string, end: string) {
  const params = new URLSearchParams({ from: start, to: end });
  if (direction.value) params.set("direction", direction.value);
  window.location.href = `/api/reports/pdf?${params.toString()}`;
}

function downloadProductionTasks() {
  const end=today();
  window.location.href=`/api/reports/pdf/production-tasks?${new URLSearchParams({from:shiftDate(end,-13),to:end})}`;
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
    <p class="page-lead">按实际流转日期和方向导出，不包含作废记录和照片原图。</p>
    <section class="quick-copy" aria-label="复制今日流转摘要">
      <span class="quick-copy-title">复制今日流转</span>
      <label class="sr-only" for="copy-direction">复制方向</label>
      <select id="copy-direction" class="select" v-model="copyDirection" :disabled="copying || locationLoading">
        <option value="WAREHOUSE_TO_PRODUCTION">仓库 → 生产车间</option>
        <option value="PRODUCTION_TO_WAREHOUSE">生产车间 → 仓库</option>
      </select>
      <button type="button" class="btn btn-secondary" :disabled="copying || locationLoading" :aria-expanded="locationEditorOpen" aria-controls="daily-location-editor" @click="toggleLocationEditor">{{ locationLoading ? '读取中…' : locationEditorOpen ? '收起库位' : '编辑库位' }}</button>
      <button type="button" class="btn btn-secondary" :disabled="copying" @click="copyToday">{{ copying ? '复制中…' : '复制今日摘要' }}</button>
      <span v-if="copyMessage" :class="copyFailed ? 'copy-error' : 'copy-success'" :role="copyFailed ? 'alert' : 'status'">{{ copyMessage }}</span>
    </section>
    <section v-if="locationEditorOpen" id="daily-location-editor" class="card card-pad location-editor" aria-label="编辑今日库位">
      <h2>今日库位 · {{ editorDate }}</h2>
      <p class="hint">逐项修改非 TT 的库位。留空按 TT 复制；修改会保存在当前浏览器中。</p>
      <p v-if="!locationRows.length" class="hint">今天暂无有效流转记录。</p>
      <div v-for="row in locationRows" :key="row.key" class="location-row">
        <div><strong>{{ row.name }}</strong><small>Code：{{ row.code }} · {{ row.quantity.toLocaleString() }}{{ row.unit }}</small></div>
        <label :for="`location-${locationRows.indexOf(row)}`">库位</label>
        <input :id="`location-${locationRows.indexOf(row)}`" v-model="locationValues[row.key]" class="input" maxlength="60" placeholder="TT" @input="saveLocations" />
      </div>
    </section>
    <p v-if="locationError" class="copy-error" role="alert">{{ locationError }}</p>
    <section class="card card-pad stack">
      <div class="field">
        <label>导出方向</label>
        <div class="direction-choice" role="group" aria-label="PDF 导出方向">
          <button type="button" :class="{ active: direction === '' }" @click="direction = ''">全部</button>
          <button type="button" :class="{ active: direction === 'WAREHOUSE_TO_PRODUCTION' }" @click="direction = 'WAREHOUSE_TO_PRODUCTION'">仓库 → 生产车间</button>
          <button type="button" :class="{ active: direction === 'PRODUCTION_TO_WAREHOUSE' }" @click="direction = 'PRODUCTION_TO_WAREHOUSE'">生产车间 → 仓库</button>
        </div>
      </div>

      <div class="field-label">快捷导出</div>
      <div class="preset-grid">
        <button class="btn btn-secondary" @click="downloadPreset('today')">导出当天</button>
        <button class="btn btn-secondary" @click="downloadPreset('yesterday')">导出昨天</button>
        <button class="btn btn-secondary" @click="downloadPreset('week')">导出本周</button>
        <button class="btn btn-secondary" @click="downloadPreset('twoWeeks')">导出近两周</button>
      </div>
      <button class="btn btn-secondary" @click="downloadProductionTasks">导出近两周生产任务记录</button>
      <hr class="divider" />
      <div class="field-label">自选导出日期</div>
      <div class="grid-2">
        <div class="field"><label>开始日期</label><input class="input" type="date" v-model="from" /></div>
        <div class="field"><label>结束日期</label><input class="input" type="date" v-model="to" /></div>
      </div>
      <div v-if="message" class="error-text" role="alert">{{ message }}</div>
      <button class="btn btn-primary btn-block" @click="downloadCustom">按所选日期导出 PDF</button>
      <p class="hint">选择“全部”会生成两个方向的明细表和总计表，共四个表；选择单一方向时生成该方向的明细表和总计表。</p>
    </section>
  </div>
</template>

<style scoped>
.quick-copy{display:flex;align-items:center;flex-wrap:wrap;gap:8px 12px;margin:16px 0;padding:10px 12px;border:1px solid var(--line);border-radius:8px;background:var(--panel)}
.quick-copy-title{font-size:12px;font-weight:650;color:var(--muted)}.quick-copy .select{width:auto;min-width:190px;max-width:100%;min-height:38px;font-size:12px}.quick-copy .btn{min-height:38px;font-size:12px}.copy-success,.copy-error{font-size:12px}.copy-success{color:var(--green)}.copy-error{color:var(--red)}
.location-editor{margin:-6px 0 16px}.location-editor h2{font-size:16px;margin:0 0 4px}.location-editor>.hint{margin:0 0 10px}.location-row{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(110px,170px);align-items:center;gap:10px;padding:10px 0;border-top:1px solid var(--line)}.location-row>div{display:grid;gap:3px;min-width:0}.location-row strong,.location-row small{overflow-wrap:anywhere}.location-row small{font-size:11px;color:var(--muted)}.location-row label{font-size:12px}.location-row input{min-width:0}
.preset-grid{display:grid;grid-template-columns:1fr 1fr;gap:9px}.divider{margin:3px 0}.error-text{background:#fdeced;border-radius:8px;padding:10px 12px}
.direction-choice{display:grid;grid-template-columns:.7fr 1.4fr 1.4fr;gap:6px}.direction-choice button{border:1px solid #b9c7ce;background:#fff;border-radius:8px;padding:9px 6px;color:#314650}.direction-choice button.active{background:#174a68;border-color:#174a68;color:#fff}
@media(max-width:520px){.direction-choice{grid-template-columns:1fr}.preset-grid{grid-template-columns:1fr 1fr}.location-row{grid-template-columns:minmax(0,1fr) minmax(100px,140px)}.location-row label{grid-column:2;grid-row:1;align-self:start}.location-row input{grid-column:2;grid-row:1;margin-top:16px}}
@media(max-width:380px){.preset-grid{grid-template-columns:1fr}}
</style>
