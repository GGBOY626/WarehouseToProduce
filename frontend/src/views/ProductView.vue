<script setup lang="ts">
import { onMounted, ref } from "vue";
import { productsApi } from "../api";
import { errorMessage } from "../api/http";
import type { Product } from "../types";
const rows = ref<Product[]>([]),
  q = ref(""),
  editing = ref<Product>(),
  showEditor = ref(false),
  form = ref({
    name: "",
    materialCode: "",
    sku: "",
    defaultUnitsPerCarton: undefined as number | undefined,
    baseUnit: "个",
  }),
  message = ref("");
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
        sku: p.sku || "",
        defaultUnitsPerCarton: p.defaultUnitsPerCarton,
        baseUnit: p.baseUnit,
      }
    : {
        name: "",
        materialCode: "",
        sku: "",
        defaultUnitsPerCarton: undefined,
        baseUnit: "个",
      };
}
async function save() {
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
        placeholder="搜索产品、物料编码或 SKU"
        @keyup.enter="load"
      /><button class="btn btn-secondary" @click="load">搜索</button>
    </div>
    <button class="btn btn-primary btn-block add" @click="open()">
      ＋ 新增产品
    </button>
    <section v-if="showEditor" class="card card-pad stack editor">
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
        <label>SKU</label><input class="input mono" v-model.trim="form.sku" />
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
          />
        </div>
        <div class="field">
          <label class="required">基础单位</label
          ><input
            class="input"
            v-model.trim="form.baseUnit"
            placeholder="个 / 袋 / 瓶"
          />
        </div>
      </div>
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
          <div v-if="p.duplicateName" class="duplicate-warning">
            同名产品，请核对物料编码
          </div>
          <div class="hint">
            <span v-if="p.sku" class="mono">SKU {{ p.sku }} · </span
            >{{
              p.defaultUnitsPerCarton
                ? `${p.defaultUnitsPerCarton} ${p.baseUnit}/箱`
                : "未设置箱规"
            }}
          </div>
        </div>
        <span :class="['status', p.active ? 'ok' : 'neutral']">{{
          p.active ? "启用" : "停用"
        }}</span
        ><button class="btn btn-ghost" @click="open(p)">编辑</button
        ><button class="btn btn-secondary" @click="toggle(p)">
          {{ p.active ? "停用" : "启用" }}
        </button>
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
</style>
