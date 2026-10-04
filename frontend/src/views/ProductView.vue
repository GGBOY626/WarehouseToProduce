<script setup lang="ts">
import { nextTick, onMounted, ref } from "vue";
import { productsApi } from "../api";
import { errorMessage } from "../api/http";
import type { Product, ProductUsage } from "../types";
import { useAuth } from "../auth";
const { authenticated } = useAuth();
const rows = ref<Product[]>([]),
  q = ref(""),
  editing = ref<Product>(),
  showEditor = ref(false),
  form = ref({
    name: "",
    materialCode: "",
    materialBatch: "",
    movementDirection: "" as ProductUsage | "",
    defaultUnitsPerCarton: undefined as number | undefined,
    baseUnit: "个",
    quantityUnknown: false,
  }),
  message = ref("");
const editorSection = ref<HTMLElement>();
async function load() {
  rows.value = await productsApi.search(q.value, true);
}
function open(p?: Product) {
  showEditor.value = true;
  editing.value = p;
  form.value = p
    ? {
        name: p.name,
        materialCode: p.materialCode,
        materialBatch: p.materialBatch || "",
        movementDirection: p.movementDirection || "",
        defaultUnitsPerCarton: p.defaultUnitsPerCarton,
        baseUnit: p.baseUnit || "",
        quantityUnknown: p.quantityUnknown,
      }
    : {
        name: "",
        materialCode: "",
        materialBatch: "",
        movementDirection: "" as ProductUsage | "",
        defaultUnitsPerCarton: undefined,
        baseUnit: "个",
        quantityUnknown: false,
      };
  void nextTick(() => editorSection.value?.scrollIntoView({ behavior: "smooth", block: "start" }));
}
function quantityTypeChanged() {
  if (form.value.quantityUnknown) {
    form.value.defaultUnitsPerCarton = undefined;
    form.value.baseUnit = "";
  }
}
async function save() {
  if (!form.value.movementDirection) {
    message.value = "请选择产品流转分类。";
    return;
  }
  try {
    if (editing.value) await productsApi.update(editing.value.id, form.value);
    else await productsApi.create(form.value);
    showEditor.value = false;
    editing.value = undefined;
    await load();
  } catch (e) {
    message.value = errorMessage(e);
  }
}
async function toggle(p: Product) {
  if (!confirm(`${p.active ? "停用" : "启用"}产品“${p.name}”？`)) return;
  await productsApi.status(p.id, !p.active);
  await load();
}
async function remove(p: Product) {
  if (!confirm(`确定永久删除产品“${p.name}”吗？删除后无法恢复。`)) return;
  message.value = "";
  try {
    await productsApi.delete(p.id);
    await load();
  } catch (e) {
    message.value = errorMessage(e);
    alert(message.value);
  }
}
onMounted(load);
</script>
<template>
  <div class="page">
    <h1 class="page-title">产品管理</h1>
    <p class="page-lead">
      同名产品可按不同物料编码分别维护，登记时请核对编码。
    </p>
    <div v-if="message" class="error-text">{{ message }}</div>
    <div class="row">
      <input
        class="input grow"
        v-model="q"
        placeholder="搜索产品、物料编码或物料批次"
        @keyup.enter="load"
      /><button class="btn btn-secondary" @click="load">搜索</button>
    </div>
    <button v-if="authenticated" class="btn btn-primary btn-block add" @click="open()">
      ＋ 新增产品
    </button>
    <section ref="editorSection" v-if="authenticated && showEditor" class="card card-pad stack editor">
      <div class="field">
        <label class="required">产品名称</label
        ><input class="input" v-model.trim="form.name" />
      </div>
      <div class="field">
        <label class="required">物料编码</label
        ><input
          class="input mono"
          v-model.trim="form.materialCode"
          placeholder="每个产品必须填写"
        />
      </div>
      <div class="field">
        <label>物料批次（可选）</label
        ><input
          class="input mono"
          v-model.trim="form.materialBatch"
          placeholder="例如 CA202607006"
        />
      </div>
      <div class="field">
        <label class="required">流转分类</label>
        <div class="usage-choice" role="group" aria-label="产品流转分类">
          <button type="button" :class="{active:form.movementDirection==='WAREHOUSE_TO_PRODUCTION'}" @click="form.movementDirection='WAREHOUSE_TO_PRODUCTION'">仓库 → 生产车间</button>
          <button type="button" :class="{active:form.movementDirection==='PRODUCTION_TO_WAREHOUSE'}" @click="form.movementDirection='PRODUCTION_TO_WAREHOUSE'">生产车间 → 仓库</button>
        </div>
        <small class="hint">该产品只会出现在对应方向的产品搜索中。</small>
      </div>
      <div class="grid-2">
        <div class="field">
          <label>默认每箱数量</label
          ><input
            class="input"
            type="number"
            min="1"
            inputmode="numeric"
            v-model.number="form.defaultUnitsPerCarton"
            :disabled="form.quantityUnknown"
          />
        </div>
        <div class="field">
          <label :class="{required:!form.quantityUnknown}">基础单位</label
          ><input
            class="input"
            v-model.trim="form.baseUnit"
            :disabled="form.quantityUnknown"
            placeholder="个 / 袋 / 瓶"
          />
        </div>
      </div>
      <label class="unknown-quantity">
        <input type="checkbox" v-model="form.quantityUnknown" @change="quantityTypeChanged" />
        该产品数量不确定（无固定计量单位或无法清点）
      </label>
      <div class="row">
        <button class="btn btn-primary grow" @click="save">保存产品</button
        ><button class="btn btn-secondary" @click="showEditor = false">
          取消
        </button>
      </div>
    </section>
    <div class="stack list">
      <article v-for="p in rows" :key="p.id" class="card card-pad row">
        <div class="grow">
          <strong>{{ p.name }}</strong>
          <div class="material-code mono">物料编码：{{ p.materialCode }}</div>
          <div v-if="p.materialBatch" class="hint mono">
            物料批次：{{ p.materialBatch }}
          </div>
          <span v-if="p.movementDirection" class="usage-badge">{{ p.movementDirection === 'WAREHOUSE_TO_PRODUCTION' ? '仓库 → 生产车间' : '生产车间 → 仓库' }}</span>
          <span v-else class="status warn">未分类，请编辑</span>
          <div v-if="p.duplicateName" class="duplicate-warning">
            同名产品，请核对物料编码
          </div>
          <div class="hint">
            {{
              p.quantityUnknown
                ? "数量不确定"
                : p.defaultUnitsPerCarton
                ? `${p.defaultUnitsPerCarton} ${p.baseUnit}/箱`
                : "未设置箱规"
            }}
          </div>
        </div>
        <span :class="['status', p.active ? 'ok' : 'neutral']">{{
          p.active ? "启用" : "停用"
        }}</span
        ><button v-if="authenticated" class="btn btn-ghost" @click="open(p)">编辑</button
        ><button v-if="authenticated" class="btn btn-secondary" @click="toggle(p)">
          {{ p.active ? "停用" : "启用" }}
        </button><button v-if="authenticated" class="btn btn-danger" @click="remove(p)">删除</button>
      </article>
    </div>
  </div>
</template>
<style scoped>
.add {
  margin: 12px 0;
}
.editor {
  margin-bottom: 14px;
  scroll-margin-top: 76px;
}
.list .row {
  flex-wrap: wrap;
}
.list .btn {
  min-height: 40px;
  padding: 7px 11px;
}
.material-code {
  margin: 5px 0;
  color: #174a68;
  font-size: 14px;
  font-weight: 680;
}
.duplicate-warning {
  margin-bottom: 5px;
  color: #b75b18;
  font-size: 13px;
  font-weight: 680;
}
.unknown-quantity { display: flex; align-items: center; gap: 8px; min-height: 42px; color: #174a68; font-size: 14px; font-weight: 680; }
.unknown-quantity input { width: 18px; height: 18px; accent-color: #174a68; }
.usage-choice{display:grid;grid-template-columns:1fr 1fr;gap:8px}.usage-choice button{border:1px solid #b9c7ce;background:#fff;border-radius:8px;padding:10px;color:#314650}.usage-choice button.active{background:#174a68;border-color:#174a68;color:#fff}.usage-badge{display:inline-block;margin:4px 0;padding:4px 8px;border-radius:999px;background:#e9f2f6;color:#174a68;font-size:12px;font-weight:680}
@media(max-width:420px){.usage-choice{grid-template-columns:1fr}}
</style>
