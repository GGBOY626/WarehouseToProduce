<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { movementsApi } from "../api";
import { errorMessage } from "../api/http";
import type { MovementDetail } from "../types";
import { directionLabel, issueLabels } from "../utils/labels";
import { showDateTime, nowLocalInput } from "../utils/time";
import { createUuid } from "../utils/uuid";
import { queuePhotoUploads } from "../utils/photoUploadQueue";
const route = useRoute(),
  router = useRouter(),
  id = Number(route.params.id);
const data = ref<MovementDetail>(),
  message = ref(""),
  uploading = ref(false);
async function load() {
  try {
    data.value = await movementsApi.detail(id);
  } catch (e) {
    message.value = errorMessage(e);
  }
}
async function addPhoto(e: Event) {
  const files = Array.from((e.target as HTMLInputElement).files || []);
  uploading.value = true;
  await queuePhotoUploads(id, files);
  uploading.value = false;
}
function photoQueueChanged(event:Event){const detail=(event as CustomEvent).detail;if(detail.movementId===id&&detail.status==='complete')void load()}
async function deletePhoto(photoId: number) {
  if (!confirm("确定删除这张照片？")) return;
  await movementsApi.deletePhoto(id, photoId);
  await load();
}
async function voidRecord() {
  const reason = prompt("请输入作废原因：");
  if (!reason?.trim()) return;
  try {
    await movementsApi.void(id, reason);
    await load();
  } catch (e) {
    message.value = errorMessage(e);
  }
}
function duplicate() {
  if (!data.value) return;
  const m = data.value;
  const key = `movement-draft:${m.direction}`;
  localStorage.setItem(
    key,
    JSON.stringify({
      idempotencyKey: createUuid(),
      direction: m.direction,
      movementTime: nowLocalInput(),
      senderPersonId: m.senderPersonId,
      receiverPersonId: m.receiverPersonId,
      manufactureLot: "",
      remarks: "",
      items: m.items.map((x) => ({
        productId: x.productId,
        product: {
          id: x.productId,
          name: x.productName,
          materialCode: x.batchNo,
          defaultUnitsPerCarton: x.unitsPerCarton,
          baseUnit: x.baseUnit,
          active: true,
        },
        batchNo: x.batchNo,
        fullCartons: 0,
        looseUnits: 0,
        remarks: "",
        issues: [],
        manualTotal: false,
      })),
    }),
  );
  router.push(
    `/movements/new/${m.direction === "WAREHOUSE_TO_PRODUCTION" ? "warehouse-to-production" : "production-to-warehouse"}`,
  );
}
onMounted(()=>{window.addEventListener('photo-upload-queue',photoQueueChanged);void load()});
onBeforeUnmount(()=>window.removeEventListener('photo-upload-queue',photoQueueChanged));
</script>
<template>
  <div class="page">
    <div v-if="message" class="form-alert">{{ message }}</div>
    <div v-if="!data" class="card empty">正在读取记录…</div>
    <template v-else
      ><div class="detail-top">
        <div>
          <span
            :class="[
              'status',
              data.status === 'VOID'
                ? 'neutral'
                : data.missingPhoto
                  ? 'danger'
                  : 'ok',
            ]"
            >{{
              data.status === "VOID"
                ? "已作废"
                : data.missingPhoto
                  ? "缺少照片"
                  : "有照片"
            }}</span
          ><span v-if="data.hasIssues" class="status warn">存在异常</span>
          <h1 class="page-title mono">{{ data.recordNo }}</h1>
        </div>
      </div>
      <section class="card card-pad facts">
        <div
          :class="[
            'direction-strip',
            data.direction === 'PRODUCTION_TO_WAREHOUSE' && 'return',
          ]"
        >
          {{ directionLabel(data.direction) }}
        </div>
        <dl>
          <div>
            <dt>实际时间</dt>
            <dd>{{ showDateTime(data.movementTime) }}</dd>
          </div>
          <div>
            <dt>发送人</dt>
            <dd>{{ data.senderName }}</dd>
          </div>
          <div>
            <dt>接收人</dt>
            <dd>{{ data.receiverName }}</dd>
          </div>
        </dl>
        <p v-if="data.remarks" class="remark">{{ data.remarks }}</p>
        <div v-if="data.status === 'VOID'" class="void-box">
          作废原因：{{ data.voidReason }}
        </div>
      </section>
      <section class="section">
        <h2 class="section-title">产品明细</h2>
        <article
          v-for="item in data.items"
          :key="item.id"
          class="card card-pad item"
        >
          <div>
            <strong>{{ item.productName }}</strong>
          </div>
          <div class="batch mono">物料编码：{{ item.batchNo }}</div>
          <div
            v-if="data.manufactureLot && item === data.items[0]"
            class="batch mono"
          >
            物料批次：{{ data.manufactureLot }}
          </div>
          <div class="quantities">
            <span
              ><small>完整箱</small><b>{{ item.fullCartons }}</b></span
            ><span
              ><small>散装</small
              ><b>{{ item.looseUnits }} {{ item.baseUnit }}</b></span
            ><span
              ><small>总数量</small
              ><b
                >{{ item.totalUnits ?? "未知"
                }}<template v-if="item.totalUnits != null">
                  {{ item.baseUnit }}</template
                ></b
              ></span
            >
          </div>
          <p v-if="item.totalUnitsOverridden" class="adjusted">
            人工调整；按箱规应为 {{ item.calculatedTotalUnits ?? "未知" }}
          </p>
          <div v-if="item.issues.length" class="issue-list">
            <span v-for="x in item.issues" :key="x.id" class="status warn"
              >{{ issueLabels[x.type]
              }}<template v-if="x.description"
                >：{{ x.description }}</template
              ></span
            >
          </div>
          <p v-if="item.remarks" class="remark">{{ item.remarks }}</p>
        </article>
      </section>
      <section class="section">
        <div class="section-head">
          <h2 class="section-title">照片</h2>
          <span class="hint">{{ data.photos.length }} / 10</span>
        </div>
        <div v-if="data.photos.length" class="photo-grid">
          <figure v-for="p in data.photos" :key="p.id">
            <a :href="p.url" target="_blank"
              ><img :src="p.url" :alt="p.originalName" /></a
            ><button
              v-if="data.status === 'ACTIVE'"
              @click="deletePhoto(p.id)"
              aria-label="删除照片"
            >
              ×
            </button>
          </figure>
        </div>
        <div v-else class="missing-photo">
          这条记录还没有照片，可以稍后补拍。
        </div>
        <label
          v-if="data.status === 'ACTIVE' && data.photos.length < 10"
          class="btn btn-secondary btn-block upload"
          >{{ uploading ? "正在上传…" : "＋ 补拍 / 上传照片"
          }}<input
            class="sr-only"
            type="file"
            accept="image/*"
            multiple
            :disabled="uploading"
            @change="addPhoto"
        /></label>
      </section>
      <section class="section actions" v-if="data.status === 'ACTIVE'">
        <RouterLink class="btn btn-primary" :to="`/movements/${id}/edit`"
          >编辑记录</RouterLink
        ><button class="btn btn-secondary" @click="duplicate">复制记录</button
        ><button class="btn btn-danger" @click="voidRecord">作废记录</button>
      </section></template
    >
  </div>
</template>
<style scoped>
.form-alert {
  background: #fdeced;
  color: #8f1d25;
  padding: 12px;
  border-radius: 9px;
  margin-bottom: 12px;
}
.detail-top {
  display: flex;
  justify-content: space-between;
}
.detail-top .status {
  margin: 0 6px 10px 0;
}
.facts dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  margin: 18px 0 0;
}
.facts dt {
  font-size: 12px;
  color: #66747c;
}
.facts dd {
  margin: 4px 0 0;
  font-weight: 680;
}
.remark {
  white-space: pre-wrap;
  background: #f4f6f7;
  padding: 10px;
  border-radius: 8px;
  font-size: 14px;
}
.void-box {
  background: #edf0f2;
  padding: 12px;
  border-radius: 8px;
  margin-top: 12px;
}
.item {
  margin-top: 10px;
}
.batch {
  margin-top: 10px;
  color: #174a68;
}
.quantities {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
  margin-top: 12px;
}
.quantities span {
  background: #f3f7f8;
  padding: 9px;
  border-radius: 8px;
  display: grid;
  gap: 4px;
}
.quantities small {
  font-size: 11px;
  color: #66747c;
}
.quantities b {
  overflow-wrap: anywhere;
}
.adjusted {
  font-size: 12px;
  color: #b75b18;
}
.issue-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}
.photo-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.photo-grid figure {
  margin: 0;
  aspect-ratio: 1;
  position: relative;
  border-radius: 8px;
  overflow: hidden;
}
.photo-grid img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.photo-grid button {
  position: absolute;
  right: 4px;
  top: 4px;
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.68);
  color: #fff;
  font-size: 20px;
}
.missing-photo {
  border: 1px solid #f0c4c7;
  background: #fdeced;
  color: #8f1d25;
  border-radius: 9px;
  padding: 18px;
  text-align: center;
}
.upload {
  margin-top: 10px;
}
.actions {
  display: grid;
  gap: 9px;
}
@media (max-width: 380px) {
  .facts dl {
    grid-template-columns: 1fr;
  }
  .quantities {
    grid-template-columns: 1fr;
  }
}
</style>
