<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { http, errorMessage } from "../api/http";
import { useAuth } from "../auth";
import type { Direction } from "../types";
import { directionLabel } from "../utils/labels";
import { prepareRecordOriginalPhoto, RecordPhotoPreparationError } from "../utils/recordOriginalPhoto";
import axios from "axios";

const props = defineProps<{ from: string; to: string; direction: Direction }>();
interface Original { id: number; recordDate: string; direction: Direction; originalName: string; url: string }
const { authenticated } = useAuth();
const photos = ref<Original[]>([]);
const loading = ref(false), uploading = ref(false), expanded = ref(false);
const message = ref(""), loadError = ref("");
const uploadDate = ref(props.to);
type UploadStatus = "preparing" | "uploading" | "uploaded" | "error";
interface UploadItem { id: number; name: string; originalSize: number; compressedSize?: number; progress: number; status: UploadStatus; error?: string }
const uploadItems = ref<UploadItem[]>([]);
const completedUploads = computed(() => uploadItems.value.filter(item => item.status === "uploaded").length);
const settledUploads = computed(() => uploadItems.value.filter(item => item.status === "uploaded" || item.status === "error").length);
const activeUploads = computed(() => uploadItems.value.filter(item => item.status === "uploading").length);
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
function sizeLabel(bytes?: number) {
  if (bytes === undefined) return "—";
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)}MB` : `${Math.max(1, Math.round(bytes / 1024))}KB`;
}
function uploadError(error: unknown) {
  if (error instanceof RecordPhotoPreparationError) return error.message;
  if (axios.isAxiosError(error)) {
    if (error.response?.status === 413) return "照片超过服务器允许大小。";
    if (error.code === "ECONNABORTED" || error.message.toLowerCase().includes("timeout")) return "上传超时，请检查网络后重试。";
    const code = error.response?.data?.code;
    if (code === "PHOTO_TOO_LARGE") return "照片文件过大，请重新选择。";
    if (code === "PHOTO_FORMAT_UNSUPPORTED") return "暂不支持该照片格式，请使用 JPG 或 PNG。";
    if (error.response && error.response.status >= 500) return "服务器保存照片失败，请稍后重试。";
    if (!error.response) return "网络连接失败，请检查网络后重试。";
  }
  return "服务器保存照片失败，请稍后重试。";
}
async function upload(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  input.value = "";
  if (!files.length || !uploadDate.value) return;
  const date = uploadDate.value, direction = props.direction;
  uploading.value = true;
  message.value = "";
  uploadItems.value = files.map((file, id) => ({id, name: file.name, originalSize: file.size, progress: 0, status: "preparing"}));
  let next = 0;
  async function worker() {
    while (next < files.length) {
      const index = next++;
      const source = files[index];
      const item = uploadItems.value[index];
      try {
        const prepared = await prepareRecordOriginalPhoto(source);
        item.compressedSize = prepared.size;
        item.status = "uploading";
        const form = new FormData();
        form.append("file", prepared); form.append("date", date); form.append("direction", direction);
        await http.post("/record-originals", form, {
          timeout: 90000,
          onUploadProgress: progress => item.progress = progress.total ? Math.round(progress.loaded / progress.total * 100) : 0,
        });
        item.progress = 100;
        item.status = "uploaded";
      } catch (error) {
        item.status = "error";
        item.error = uploadError(error);
      }
    }
  }
  try {
    await Promise.all(Array.from({length: Math.min(2, files.length)}, () => worker()));
    const failed = files.length - completedUploads.value;
    message.value = failed
      ? `已上传 ${completedUploads.value} 张，${failed} 张未成功，请查看下方原因后重试。`
      : `已上传 ${completedUploads.value} 张，归档到 ${date} · ${directionLabel(direction)}`;
  } catch (error) { message.value = uploadError(error); }
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
        <label class="btn btn-primary">{{ uploading ? '正在上传…' : '上传原件照片' }}<input class="sr-only" type="file" accept="image/*" multiple :disabled="uploading || !uploadDate" @change="upload" /></label>
        <small class="hint">上传至{{ directionLabel(direction) }}，支持多张照片，每张原图不超过 12MB；上传前会优化为清晰的记录表照片。</small>
      </div>
      <div v-if="uploadItems.length" class="upload-progress" aria-live="polite">
        <strong>{{ uploading ? `正在上传 ${Math.min(settledUploads + activeUploads, uploadItems.length)} / ${uploadItems.length}` : `本次上传 ${completedUploads} / ${uploadItems.length}` }}</strong>
        <div v-for="item in uploadItems" :key="item.id" class="upload-item">
          <div><span :title="item.name">{{ item.name }}</span><b>{{ item.status === 'preparing' ? '正在压缩…' : item.status === 'uploading' ? `上传中 ${item.progress}%` : item.status === 'uploaded' ? '上传成功' : '上传失败' }}</b></div>
          <small>原始：{{ sizeLabel(item.originalSize) }}　压缩后：{{ sizeLabel(item.compressedSize) }}</small>
          <progress v-if="item.status === 'uploading'" max="100" :value="item.progress">{{ item.progress }}%</progress>
          <small v-if="item.error" class="upload-error">{{ item.error }}</small>
        </div>
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
.upload-progress { display: grid; gap: 8px; margin: 12px 0; padding: 12px; border-radius: 8px; background: #f3f8fa; }
.upload-item { display: grid; gap: 4px; padding-top: 8px; border-top: 1px solid #d9e5e9; }
.upload-item div { display: flex; justify-content: space-between; gap: 12px; }
.upload-item div span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.upload-item b { flex: none; font-size: 12px; color: #267566; }
.upload-item progress { width: 100%; height: 8px; accent-color: #267566; }
.upload-item small { color: #60737d; }
.upload-item .upload-error { color: #a52a2a; }
.original-day h3 { font-size: 14px; margin: 16px 0 8px; }
.original-grid { display: grid; grid-template-columns: repeat(auto-fill,minmax(140px,1fr)); gap: 12px; }
.original-grid a { color: #174a68; font-size: 12px; }
.original-grid img { width: 100%; height: 170px; object-fit: contain; background: #f3f7f8; border: 1px solid #dce2e5; border-radius: 8px; }
</style>
