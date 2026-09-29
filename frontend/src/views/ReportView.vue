<script setup lang="ts">
import { ref } from "vue";
import { today } from "../utils/time";
const from = ref(today()),
  to = ref(today());
function download() {
  window.location.href = `/api/reports/pdf?from=${from.value}&to=${to.value}`;
}
</script>
<template>
  <div class="page">
    <h1 class="page-title">导出 PDF</h1>
    <p class="page-lead">按实际流转日期导出，默认不包含作废记录和照片原图。</p>
    <section class="card card-pad stack">
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
      <button class="btn btn-primary btn-block" @click="download">
        下载 PDF
      </button>
      <p class="hint">
        PDF
        包含记录编号、方向、发送人、接收人、产品、物料编码、物料批次、数量、异常、照片状态和备注。
      </p>
    </section>
  </div>
</template>
