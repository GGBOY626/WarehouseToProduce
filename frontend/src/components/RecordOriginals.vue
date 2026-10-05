<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { http, errorMessage } from "../api/http";
import { useAuth } from "../auth";
import type { Direction } from "../types";
import { directionLabel } from "../utils/labels";

const props = defineProps<{ from: string; to: string; direction: Direction }>();
interface Original { id: number; recordDate: string; direction: Direction; originalName: string; url: string }
const { authenticated } = useAuth();
const photos = ref<Original[]>([]);
const loading = ref(false), uploading = ref(false), expanded = ref(false);
const message = ref(""), loadError = ref("");
const uploadDate = ref(props.to);
let requestId = 0;
const groups = computed(() => [...new Set(photos.value.map(p => p.recordDate))].map(date => ({date, photos: photos.value.filter(p => p.recordDate === date)})));
async function load() {
  const current = ++requestId;
  photos.value = [];
  loadError.value = "";
  loading.value = false;
  if (!props.from || !props.to || props.from > props.to) return;
  loading.value = true;
  try {
    const result = await http.get<Original[]>("/record-originals", {params: {from: props.from, to: props.to, direction: props.direction}});
    if (current === requestId) photos.value = result.data;
  } catch (error) {
    if (current === requestId) loadError.value = errorMessage(error);
  } finally { if (current === requestId) loading.value = false; }
}
watch(() => [props.from, props.to, props.direction], () => { uploadDate.value = props.to; message.value = ""; void load(); }, { immediate: true });
onBeforeUnmount(() => ++requestId);
async function upload(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = "";
  if (!files.length || !uploadDate.value) return;
  const date = uploadDate.value, direction = props.direction;
  uploading.value = true;
  message.value = "";
  let done = 0;
  try {
    for (const file of files) {
      if (file.size > 12 * 1024 * 1024) throw new Error(`${file.name} 超过 12MB，请选择较小的照片。`);
      const form = new FormData();
      form.append("file", file); form.append("date", date); form.append("direction", direction);
      await http.post("/record-originals", form, {timeout: 60000});
      done++;
    }
    message.value = `已上传 ${done} 张，归档到 ${date} · ${directionLabel(direction)}`;
  } catch (error) { message.value = `已上传 ${done} 张。${error instanceof Error && error.message.includes('12MB') ? error.message : errorMessage(error)} 未成功的照片可重新选择上传。`; }
  finally { uploading.value = false; await load(); }
}
</script>

<template>
  <section class="originals card card-pad">
    <button class="originals-toggle" type="button" :aria-expanded="expanded" aria-controls="record-originals-panel" @click="expanded = !expanded">
      <strong>出入库记录表原件</strong><span>{{ loading ? '读取中…' : `${photos.length} 张` }} · {{ expanded ? '收起' : authenticated ? '查看 / 上传' : '查看' }} {{ expanded ? '⌃' : '⌄' }}</span>
    </button>
    <div v-show="expanded" id="record-originals-panel">
      <p class="hint">{{ from }} ～ {{ to }} · {{ directionLabel(direction) }}</p>
      <div v-if="authenticated" class="upload-originals">
        <label>原件日期 <input class="input" type="date" v-model="uploadDate" :disabled="uploading" /></label>
        <label class="btn btn-primary">{{ uploading ? '正在上传…' : '上传原件照片' }}<input class="sr-only" type="file" accept="image/jpeg,image/png" multiple :disabled="uploading || !uploadDate" @change="upload" /></label>
        <small class="hint">上传至{{ directionLabel(direction) }}，支持多张 JPEG / PNG，每张不超过 12MB，保留原图清晰度。</small>
      </div>
      <p v-if="message" role="status">{{ message }}</p>
      <p v-if="loadError" role="alert">{{ loadError }} <button class="btn" @click="load">重试</button></p>
      <p v-else-if="loading" role="status">正在读取原件…</p>
      <p v-else-if="!photos.length" class="hint">该日期范围及方向暂无原件照片。</p>
      <div v-for="group in groups" :key="group.date" class="original-day">
        <h3>{{ group.date }}</h3>
        <div class="original-grid">
          <a v-for="(photo, index) in group.photos" :key="photo.id" :href="photo.url" target="_blank" rel="noopener" :aria-label="`查看 ${group.date} ${directionLabel(photo.direction)} 原件 ${index + 1}`">
            <img :src="photo.url" :alt="`${group.date} 原件 ${index + 1}`" loading="lazy" />
            <span>原件 {{ index + 1 }} · 点击查看大图</span>
          </a>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.originals { margin-top: 16px; }
.originals-toggle { width: 100%; display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; border: 0; background: transparent; padding: 4px 0; min-height: 44px; color: #174a68; font: inherit; cursor: pointer; text-align: left; }
.originals-toggle span { color: #60737d; font-size: 13px; }
.originals-toggle:focus-visible,.original-grid a:focus-visible { outline: 3px solid #267566; outline-offset: 3px; }
.upload-originals { display: flex; gap: 12px; align-items: end; flex-wrap: wrap; margin: 12px 0; }
.upload-originals small { flex-basis: 100%; }
.upload-originals label:first-child { display: grid; gap: 5px; }
.upload-originals .btn:focus-within { outline: 3px solid #267566; outline-offset: 3px; }
.original-day h3 { font-size: 14px; margin: 16px 0 8px; }
.original-grid { display: grid; grid-template-columns: repeat(auto-fill,minmax(140px,1fr)); gap: 12px; }
.original-grid a { color: #174a68; font-size: 12px; }
.original-grid img { width: 100%; height: 170px; object-fit: contain; background: #f3f7f8; border: 1px solid #dce2e5; border-radius: 8px; }
</style>
