<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from "vue";
import { productsApi } from "../api";
import { errorMessage } from "../api/http";
import type { Product, ProductUsage, BoxPacking } from "../types";
import { availableCartonSpecs } from "../utils/cartonSpecs";
import { packingError } from "../utils/boxPacking";
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
    baseUnit: "个",
    quantityUnknown: false,
  }),
  message = ref("");
const loading=ref(false), saving=ref(false), busyId=ref<number>(), success=ref('');
let requestId=0;
const cartonSpecsText = ref("");
const boxPackings=ref<BoxPacking[]>([]);
const batchesText = ref("");
const productionNumbersText = ref("");
const selectedDirection = ref<ProductUsage | "">("WAREHOUSE_TO_PRODUCTION");
const visibleRows = computed(() => rows.value.filter(p => (p.movementDirection || "") === selectedDirection.value));
const editorSection = ref<HTMLElement>();
async function load() {
  const current=++requestId;loading.value=true;message.value='';
  try{const result=await productsApi.search(q.value,true);if(current===requestId)rows.value=result}
  catch(e){if(current===requestId)message.value=errorMessage(e)}
  finally{if(current===requestId)loading.value=false}
}
function open(p?: Product) {
  if(saving.value)return;
  message.value='';success.value='';
  document.querySelectorAll('.product-menu[open]').forEach(menu=>menu.removeAttribute('open'));
  showEditor.value = true;
  editing.value = p;
  batchesText.value = (p?.materialBatches || (p?.materialBatch ? [p.materialBatch] : [])).join("\n");
  productionNumbersText.value = (p?.productionNumbers || []).join("\n");
  boxPackings.value=(p?.boxPackings||[]).map(x=>({...x}));
  cartonSpecsText.value = [p?.defaultUnitsPerCarton,...(p?.cartonSpecs||[])].filter((n):n is number=>n!=null).filter(n=>!boxPackings.value.some(x=>x.boxesPerCarton*x.unitsPerBox===n)).filter((n,i,a)=>a.indexOf(n)===i).join("、");
  form.value = p
    ? {
        name: p.name,
        materialCode: p.materialCode,
        materialBatch: p.materialBatch || "",
        movementDirection: p.movementDirection || "",
        baseUnit: p.baseUnit || "",
        quantityUnknown: p.quantityUnknown,
      }
    : {
        name: "",
        materialCode: "",
        materialBatch: "",
        movementDirection: selectedDirection.value,
        baseUnit: "个",
        quantityUnknown: false,
      };
  void nextTick(() => { editorSection.value?.scrollIntoView({ behavior: 'instant', block: 'start' }); editorSection.value?.querySelector<HTMLInputElement>('input')?.focus({preventScroll:true}); });
}
function quantityTypeChanged() {
  if (form.value.quantityUnknown) {
    form.value.baseUnit = "";
  }
}
async function save() {
  if(saving.value)return;
  message.value="";success.value="";
  if (!form.value.movementDirection) {
    message.value = "请选择产品流转分类。";
    return;
  }
  try {
    const cartonSpecs = cartonSpecsText.value.split(/[,，、\s]+/).filter(Boolean).map(Number);
    if (cartonSpecs.length > 50 || cartonSpecs.some(n => !Number.isInteger(n) || n <= 0 || n > 2147483647)) {
      message.value = "常用规格最多 50 项，请输入正整数，用逗号或空格分隔。"; return;
    }
    if(boxPackings.value.length>50){message.value='包装规格最多 50 项。';return;}
    for(const packing of boxPackings.value){
      // Vue's numeric model emits an empty string when an optional input is cleared.
      if((packing.cartonsPerPallet as unknown) === '')packing.cartonsPerPallet=null;
      const error=packingError(packing,form.value.baseUnit);if(error){message.value=error;return;}
    }
    if(new Set(boxPackings.value.map(p=>`${p.boxesPerCarton}:${p.unitsPerBox}`)).size!==boxPackings.value.length){message.value='同一箱规请只填写一次。';return;}
    const productionNumbers=productionNumbersText.value.split(/\r?\n/).map(s=>s.trim()).filter(Boolean);
    if(productionNumbers.length>100||productionNumbers.some(s=>s.length>100)){message.value='生产编号最多 100 项，每项最多 100 个字符。';return;}
    saving.value=true;
    const payload = { ...form.value, boxPackings:boxPackings.value, materialBatches: batchesText.value.split(/\r?\n/).map(s => s.trim()).filter(Boolean), productionNumbers, defaultUnitsPerCarton: !form.value.quantityUnknown && new Set(cartonSpecs).size === 1 ? cartonSpecs[0] : null, cartonSpecs: [...new Set(cartonSpecs)] };
    if (editing.value) await productsApi.update(editing.value.id, payload);
    else await productsApi.create(payload);
    showEditor.value = false;
    editing.value = undefined;
    await load();success.value="产品已保存";
  } catch (e) {
    message.value = errorMessage(e);
  } finally {saving.value=false;}
}
async function toggle(p: Product) {
  if(busyId.value!=null || !confirm(`${p.active ? "停用" : "启用"}产品“${p.name}”？`))return;
  busyId.value=p.id;message.value='';success.value='';
  try{await productsApi.status(p.id,!p.active);await load();success.value=`产品已${p.active?'停用':'启用'}`}
  catch(e){message.value=errorMessage(e)}finally{busyId.value=undefined}
}
async function remove(p: Product) {
  if(busyId.value!=null || !confirm(`确定永久删除产品“${p.name}”吗？删除后无法恢复。`))return;
  busyId.value=p.id;message.value='';success.value='';
  try{await productsApi.delete(p.id);await load();success.value='产品已删除'}
  catch(e){message.value=errorMessage(e)}finally{busyId.value=undefined}
}
function productMenu(event:Event){
  const current=event.currentTarget as HTMLDetailsElement;
  if(current.open)document.querySelectorAll('.product-menu[open]').forEach(menu=>{if(menu!==current)menu.removeAttribute('open')});
}
onMounted(load);
</script>
<template>
  <div class="page">
    <div class="product-heading"><h1 class="page-title">产品管理</h1><button v-if="authenticated" class="btn btn-primary" @click="open()">＋ 新增产品</button></div>
    <p class="page-lead">
      管理物料、批次与箱规；新到货可直接补充批次。
    </p>
    <div v-if="message" class="error-text" role="alert">{{ message }}</div>
    <p v-if="success" class="success-note" role="status">{{success}}</p>
    <div class="row">
      <input
        class="input grow"
        aria-label="搜索产品、物料编码、生产编号或物料批次"
        v-model="q"
        placeholder="搜索产品、物料编码、生产编号或物料批次"
        @keyup.enter="load"
      /><button class="btn btn-secondary" @click="load">搜索</button>
    </div>
    <section ref="editorSection" v-if="authenticated && showEditor" class="card card-pad stack editor">
      <div class="field">
        <label for="product-name" class="required">产品名称</label
        ><input id="product-name" name="product-name" class="input" v-model.trim="form.name" />
      </div>
      <div class="field">
        <label for="product-code" class="required">物料编码</label
        ><input
          id="product-code" name="product-code" spellcheck="false" class="input mono"
          v-model.trim="form.materialCode"
          placeholder="每个产品必须填写"
        />
      </div>
      <div class="field">
        <label for="product-production-numbers">生产编号（可选，每行一个）</label>
        <textarea id="product-production-numbers" class="textarea mono" v-model="productionNumbersText" placeholder="P001&#10;P002" />
        <small class="hint">保存时添加新编号，已有编号保留；多个编号在流转登记时选择实际使用的编号。</small>
      </div>
      <div class="field">
        <label for="product-batches">物料批次（可选，每行一个）</label>
        <textarea id="product-batches" class="textarea mono" v-model="batchesText" placeholder="A001&#10;A002" />
        <small class="hint">保存时添加新批次，已有批次保留；流转登记时也可输入新批次。</small>
      </div>
      <div class="field">
        <label class="required">流转分类</label>
        <div class="usage-choice" role="group" aria-label="产品流转分类">
          <button type="button" :aria-pressed="form.movementDirection==='WAREHOUSE_TO_PRODUCTION'" :class="{active:form.movementDirection==='WAREHOUSE_TO_PRODUCTION'}" @click="form.movementDirection='WAREHOUSE_TO_PRODUCTION'">仓库 → 生产车间</button>
          <button type="button" :aria-pressed="form.movementDirection==='PRODUCTION_TO_WAREHOUSE'" :class="{active:form.movementDirection==='PRODUCTION_TO_WAREHOUSE'}" @click="form.movementDirection='PRODUCTION_TO_WAREHOUSE'">生产车间 → 仓库</button>
        </div>
        <small class="hint">该产品只会出现在对应方向的产品搜索中。</small>
      </div>
      <div class="grid-2">
        <div v-if="form.movementDirection!=='PRODUCTION_TO_WAREHOUSE'" class="field">
          <label for="carton-specs">默认每箱数量（可设置多个）</label>
          <input id="carton-specs" class="input" v-model="cartonSpecsText" placeholder="例如 500、1000、1800" />
          <small class="hint">用逗号、顿号或空格分隔。只有一种时自动带入；多种时，出入库须选择本次使用的规格。</small>
        </div>
        <div class="field">
          <label for="product-unit" :class="{required:!form.quantityUnknown}">基础单位</label
          ><input
            id="product-unit" class="input"
            v-model.trim="form.baseUnit"
            :disabled="form.quantityUnknown"
            placeholder="个 / 袋 / 瓶"
          />
        </div>
      </div>
      <section v-if="form.movementDirection==='PRODUCTION_TO_WAREHOUSE'" class="stack">
        <strong>默认包装规格</strong>
        <div v-for="(packing,index) in boxPackings" :key="index" class="packing-preset">
          <div class="grid-2"><div class="field"><label :for="`boxes-${index}`">每箱装几盒</label><input :id="`boxes-${index}`" class="input" type="number" min="1" step="1" inputmode="numeric" v-model.number="packing.boxesPerCarton" placeholder="例如 28" /></div><div class="field"><label :for="`units-${index}`">每盒装几{{form.baseUnit||'个'}}</label><input :id="`units-${index}`" class="input" type="number" min="1" step="1" inputmode="numeric" v-model.number="packing.unitsPerBox" placeholder="例如 30" /></div></div>
          <label class="field"><span>每托盘多少箱（可选）</span><input class="input" type="number" min="1" max="2147483647" step="1" v-model.number="packing.cartonsPerPallet" placeholder="留空则不折算托盘" /></label>
          <div class="row"><span class="hint">{{packingError(packing,form.baseUnit)||`${packing.boxesPerCarton} 盒 × ${packing.unitsPerBox} ${form.baseUnit}/盒 = ${(packing.boxesPerCarton*packing.unitsPerBox).toLocaleString()} ${form.baseUnit}/箱`}}</span><button class="btn btn-ghost" @click="boxPackings.splice(index,1)">移除规格</button></div>
        </div>
        <button class="btn btn-secondary" @click="boxPackings.push({boxesPerCarton:0,unitsPerBox:0})">＋ 添加包装规格</button>
        <p class="hint">只有一种规格时自动带入回仓记录；多种规格需选择。尾箱直接填写实际盒数。基础单位填“条”时，例如 28 盒 × 30 条 = 840 条。</p>
        <div v-if="cartonSpecsText" class="field"><label for="legacy-carton-specs">原每箱总数（待补录包装层级）</label><input id="legacy-carton-specs" class="input" v-model="cartonSpecsText" /><small class="hint">无法从总数反推盒数。补录上方规格后，请清除对应旧总数；不会修改历史流转数量。</small></div>
      </section>
      <label class="unknown-quantity">
        <input type="checkbox" v-model="form.quantityUnknown" @change="quantityTypeChanged" />
        该产品数量不确定（无固定计量单位或无法清点）
      </label>
      <div class="row">
        <button class="btn btn-primary grow" :disabled="saving" @click="save">{{saving ? '正在保存…' : '保存产品'}}</button
        ><button class="btn btn-secondary" :disabled="saving" @click="showEditor = false">
          取消
        </button>
      </div>
    </section>
    <div class="usage-choice" role="group" aria-label="按流转方向查看产品">
      <button type="button" :aria-pressed="selectedDirection==='WAREHOUSE_TO_PRODUCTION'" :class="{active:selectedDirection==='WAREHOUSE_TO_PRODUCTION'}" @click="selectedDirection='WAREHOUSE_TO_PRODUCTION'">仓库 → 生产车间</button>
      <button type="button" :aria-pressed="selectedDirection==='PRODUCTION_TO_WAREHOUSE'" :class="{active:selectedDirection==='PRODUCTION_TO_WAREHOUSE'}" @click="selectedDirection='PRODUCTION_TO_WAREHOUSE'">生产车间 → 仓库</button>
      <button v-if="rows.some(p => !p.movementDirection)" type="button" :class="{active:selectedDirection===''}" @click="selectedDirection=''">待分类</button>
    </div>
    <p v-if="loading" class="card empty" role="status">正在读取产品…</p>
    <p v-else-if="!visibleRows.length" class="card empty">暂无符合条件的产品，可调整搜索或新增产品。</p>
    <div v-else class="stack list">
      <article v-for="p in visibleRows" :key="p.id" class="card product-card">
        <component :is="authenticated ? 'button' : 'div'" class="product-main" :type="authenticated ? 'button' : undefined" :aria-label="authenticated ? `编辑 ${p.name}` : undefined" @click="authenticated && open(p)">
          <div class="product-title"><strong>{{p.name}}</strong><span class="status neutral">{{p.active ? '启用' : '停用'}}</span></div>
          <span class="material-code">编码 {{p.materialCode}}</span>
          <span v-if="p.boxPackings?.length" class="product-spec">{{p.boxPackings.map(x=>`${x.boxesPerCarton} 盒/箱 × ${x.unitsPerBox} ${p.baseUnit}/盒${x.cartonsPerPallet ? ` · ${x.cartonsPerPallet} 箱/托盘` : ''}`).join('；')}}</span>
          <span class="product-spec">{{ availableCartonSpecs(p).length ? `${availableCartonSpecs(p).join(' / ')} ${p.baseUnit || '个'}/箱` : '未设置箱规' }}<template v-if="p.quantityUnknown"> · 数量不确定</template></span>
          <span v-if="p.duplicateName" class="duplicate-warning">同名产品，请核对编码</span><span v-if="!p.movementDirection" class="duplicate-warning">未分类，请编辑</span>
        </component>
        <details v-if="authenticated" class="product-menu" @toggle="productMenu" @keydown.esc="($event.currentTarget as HTMLDetailsElement).removeAttribute('open')"><summary :aria-label="`${p.name}的更多操作`">⋯</summary><div class="product-menu-actions"><button :disabled="busyId!=null" @click="toggle(p)">{{p.active ? '停用产品' : '启用产品'}}</button><button class="danger-action" :disabled="busyId!=null" @click="remove(p)">删除产品</button></div></details>
        <details v-if="p.materialBatches?.length || p.materialBatch" class="product-batches"><summary>物料批次 · {{p.materialBatches?.length || 1}} 项</summary><p>{{p.materialBatches?.length ? p.materialBatches.join(' / ') : p.materialBatch}}</p></details>
        <details v-if="p.productionNumbers?.length" class="product-batches"><summary>生产编号 · {{p.productionNumbers.length}} 项</summary><p>{{p.productionNumbers.join(' / ')}}</p></details>
      </article>
    </div>
  </div>
</template>
<style scoped>
.editor .grid-2 { align-items: start; }
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
.usage-choice{display:flex;gap:4px;padding:4px;margin:12px 0;background:#e7edef;border-radius:9px}.usage-choice button{flex:1;min-width:0;min-height:40px;border:0;background:transparent;border-radius:6px;padding:6px;color:var(--muted);font-size:12px;cursor:pointer}.usage-choice button.active{background:white;color:var(--blue);box-shadow:0 1px 3px #17212614}.usage-badge{display:inline-block;margin:4px 0;padding:4px 8px;border-radius:999px;background:#e9f2f6;color:#174a68;font-size:12px;font-weight:680}
.product-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:8px}.product-heading .page-title{margin:0}.product-heading .btn{flex-shrink:0}.editor{margin-top:14px}.product-card{position:relative;min-width:0}.product-main{display:grid;gap:5px;width:100%;border:0;border-radius:10px;background:none;text-align:left;padding:14px 58px 12px 14px;min-width:0}.product-main:is(button){cursor:pointer}.product-title{display:flex;align-items:baseline;justify-content:space-between;gap:8px}.product-title strong{font-size:15px;overflow-wrap:anywhere}.product-title .status{flex-shrink:0}.product-main .material-code{font-size:12px;font-weight:400;color:var(--muted);margin:0;overflow-wrap:anywhere}.product-spec{font-size:12px;color:var(--muted);overflow-wrap:anywhere}.product-menu{position:absolute;top:7px;right:7px}.product-menu summary{list-style:none;width:40px;height:40px;display:grid;place-items:center;font:24px Arial,sans-serif;color:var(--blue);cursor:pointer;border-radius:7px}.product-menu summary::-webkit-details-marker{display:none}.product-menu[open] summary{background:var(--soft-blue)}.product-menu-actions{position:absolute;right:0;z-index:10;min-width:132px;border:1px solid var(--line);border-radius:9px;background:white;padding:5px;box-shadow:0 6px 18px #1721261f}.product-menu-actions button{display:block;width:100%;min-height:44px;padding:9px 12px;border:0;background:none;text-align:left;font-size:13px;cursor:pointer}.product-menu-actions .danger-action{color:var(--red)}.product-batches{margin:0 14px;border-top:1px solid #edf0f2;font-size:12px;color:var(--muted)}.product-batches summary{min-height:36px;padding:9px 0;cursor:pointer}.product-batches p{line-height:1.6;overflow-wrap:anywhere;margin:0 0 12px}.success-note{font-size:13px;color:var(--green)}.duplicate-warning{margin:0;font-size:12px}.product-main:focus-visible{outline-offset:-3px}@media(max-width:430px){.editor .grid-2{grid-template-columns:1fr}.product-title{align-items:flex-start}}
</style>
