<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { productsApi } from '../api';
import { productionRecipesApi } from '../api/productionRecipes';
import { errorMessage } from '../api/http';
import type { Product, ProductMaterialCategory, ProductionRecipe, ProductionRecipeInput } from '../types';
import SearchPicker from './SearchPicker.vue';

const props = defineProps<{ output: Product }>();
const emit = defineEmits<{ close: []; saved: [] }>();
type DraftItem = { key: number; productId?: number; productName: string; category?: ProductMaterialCategory; baseUnit: string; quantityPerOutput: number | '' };
let nextKey = 0;
const recipes = ref<ProductionRecipe[]>([]), selectedId = ref<number | ''>(''), recipeName = ref('');
const items = ref<DraftItem[]>([]), primaryId = ref<number>(), editing = ref(false);
const loading = ref(false), saving = ref(false), message = ref('');
const labels: Record<ProductMaterialCategory, string> = { PRIMARY: '主料', OUTER_BOX: '外盒', LINER: '内衬', INNER_BOX: '内盒', OTHER: '其他' };
function blankItem(): DraftItem { return { key: nextKey++, productName: '', baseUnit: '', quantityPerOutput: '' }; }
async function load() {
  loading.value = true; message.value = '';
  try { recipes.value = await productionRecipesApi.list(props.output.id); }
  catch (error) { message.value = errorMessage(error); }
  finally { loading.value = false; }
}
function startNew() {
  selectedId.value = ''; recipeName.value = ''; items.value = [blankItem()]; primaryId.value = undefined; editing.value = true; message.value = '';
}
function openRecipe() {
  const recipe = recipes.value.find(item => item.id === Number(selectedId.value));
  if (!recipe) return;
  recipeName.value = recipe.name; primaryId.value = recipe.primaryMaterialProductId;
  items.value = recipe.items.map(item => ({ key: nextKey++, productId: item.productId, productName: item.productName,
    category: item.materialCategory, baseUnit: item.baseUnit, quantityPerOutput: item.quantityPerOutput }));
  editing.value = true; message.value = '';
}
async function loadMaterials(query: string) {
  return (await productsApi.search(query, false, 'WAREHOUSE_TO_PRODUCTION')).filter(product => product.active && !product.quantityUnknown);
}
function selectMaterial(item: DraftItem, product?: Product) {
  item.productId = product?.id; item.productName = product?.name || ''; item.baseUnit = product?.baseUnit || '';
  item.category = product?.materialCategory || 'OTHER';
  if (!primaryId.value && item.productId && item.category === 'PRIMARY') primaryId.value = item.productId;
}
async function save() {
  if (saving.value) return;
  if (!recipeName.value.trim() || !items.value.length || items.value.some(item => !item.productId || typeof item.quantityPerOutput !== 'number' || !Number.isFinite(item.quantityPerOutput) || item.quantityPerOutput <= 0)) {
    message.value = '请填写配方名称，并为每种物料选择产品和大于零的用量。'; return;
  }
  if (!primaryId.value || !items.value.some(item => item.productId === primaryId.value)) { message.value = '请选择配方中的主料。'; return; }
  if (new Set(items.value.map(item => item.productId)).size !== items.value.length) { message.value = '同一种物料只添加一次。'; return; }
  const data: ProductionRecipeInput = { name: recipeName.value.trim(), primaryMaterialProductId: primaryId.value,
    items: items.value.map(item => ({ productId: item.productId!, quantityPerOutput: item.quantityPerOutput as number })) };
  saving.value = true; message.value = '';
  try {
    if (selectedId.value) await productionRecipesApi.update(Number(selectedId.value), data);
    else await productionRecipesApi.create(props.output.id, data);
    await load();
    const match = recipes.value.find(recipe => recipe.name === data.name);
    selectedId.value = match?.id ?? ''; editing.value = false; emit('saved');
  } catch (error) { message.value = errorMessage(error); }
  finally { saving.value = false; }
}
async function remove() {
  const recipe = recipes.value.find(item => item.id === Number(selectedId.value));
  if (!recipe || !confirm(`删除配方“${recipe.name}”？`)) return;
  saving.value = true; message.value = '';
  try { await productionRecipesApi.delete(recipe.id); await load(); selectedId.value = ''; editing.value = false; emit('saved'); }
  catch (error) { message.value = errorMessage(error); }
  finally { saving.value = false; }
}
onMounted(load);
</script>

<template>
  <section class="recipe-manager" aria-labelledby="recipe-manager-title">
    <header><div><span class="hint">成品配方</span><h2 id="recipe-manager-title">{{output.name}}</h2></div><button type="button" class="btn btn-secondary" :disabled="saving" @click="emit('close')">关闭</button></header>
    <p v-if="message" class="error-text" role="alert">{{message}}</p>
    <div class="recipe-toolbar">
      <label class="field grow"><span>配方模板</span><select v-model.number="selectedId" class="select" :disabled="loading || editing"><option :value="''">{{loading?'正在读取…':'选择已有配方'}}</option><option v-for="recipe in recipes" :key="recipe.id" :value="recipe.id">{{recipe.name}}</option></select></label>
      <button type="button" class="btn btn-secondary" :disabled="loading || saving || editing" @click="openRecipe">编辑</button>
      <button type="button" class="btn btn-secondary" :disabled="loading || saving || editing" @click="startNew">＋ 新建配方</button>
    </div>
    <p v-if="!recipes.length && !loading && !editing" class="hint">还没有配方模板。</p>
    <template v-if="editing">
      <label class="field"><span>配方名称 *</span><input v-model.trim="recipeName" class="input" maxlength="100" placeholder="例如：软糖袋装" /></label>
      <div class="recipe-column-head"><span>仓库物料</span><span>类别</span><span>每个计划数用量</span><span>主料</span><span></span></div>
      <div v-for="(item,index) in items" :key="item.key" class="recipe-item-row">
        <SearchPicker v-model="item.productId" v-model:text-value="item.productName" label="物料" placeholder="搜索仓库物料" :load="loadMaterials" :display="product=>product.name" @select="product=>selectMaterial(item,product)" />
        <span class="recipe-category">{{item.category ? labels[item.category] : '选择物料'}}</span>
        <label class="field"><span class="sr-only">{{item.productName || `物料 ${index+1}`}}每个计划数用量</span><input v-model.number="item.quantityPerOutput" class="input" type="number" min="0.000001" max="999999999999.999999" step="0.000001" inputmode="decimal" placeholder="例如 60" /></label>
        <label class="primary-choice"><input v-model="primaryId" type="radio" name="recipe-primary" :value="item.productId" :disabled="!item.productId" /><span>主料</span></label>
        <button type="button" class="btn btn-ghost" :aria-label="`移除物料 ${index+1}`" :disabled="items.length===1" @click="items.splice(index,1)">移除</button>
      </div>
      <button type="button" class="btn btn-secondary" :disabled="items.length>=50" @click="items.push(blankItem())">＋ 添加物料</button>
      <p class="hint">计划数量按成品的袋数或盒数计。此处填写每一个成品数所需的物料量；保存到任务后，需求量会按计划数量换算，实际批次在任务中选择。</p>
      <footer><button type="button" class="btn btn-secondary" :disabled="saving" @click="editing=false">取消</button><button v-if="selectedId" type="button" class="btn btn-ghost" :disabled="saving" @click="remove">删除配方</button><button type="button" class="btn btn-primary" :disabled="saving" @click="save">{{saving?'正在保存…':'保存配方'}}</button></footer>
    </template>
    <div v-else-if="selectedId" class="recipe-summary">
      <div v-for="item in recipes.find(recipe=>recipe.id===Number(selectedId))?.items" :key="item.productId" class="recipe-summary-row"><strong>{{item.productName}}</strong><span>{{labels[item.materialCategory]}}</span><span>{{item.quantityPerOutput}} / 个计划数</span><span v-if="item.primary" class="primary-tag">主料</span></div>
    </div>
  </section>
</template>

<style scoped>
.recipe-manager{margin:16px 0 20px;padding:16px 0;border-top:1px solid var(--line);border-bottom:1px solid var(--line)}
.recipe-manager header,.recipe-manager footer,.recipe-toolbar{display:flex;align-items:center;gap:10px;justify-content:space-between}
.recipe-manager h2{margin:2px 0 12px;font-size:20px;overflow-wrap:anywhere}
.recipe-toolbar{justify-content:flex-start;margin-bottom:12px}.recipe-toolbar .grow{flex:1;min-width:120px}
.recipe-column-head,.recipe-item-row,.recipe-summary-row{display:grid;grid-template-columns:minmax(160px,2fr) minmax(70px,.7fr) minmax(135px,1fr) minmax(70px,.6fr) auto;gap:10px;align-items:center}
.recipe-column-head{padding:8px 0;color:var(--muted);font-size:12px}.recipe-item-row{padding:8px 0;border-top:1px solid var(--line)}
.recipe-item-row .field{margin:0}.recipe-category{font-size:13px;color:var(--muted)}.primary-choice{display:flex;align-items:center;gap:5px;min-height:42px;font-size:13px}.recipe-manager footer{justify-content:flex-end;margin-top:16px}
.recipe-summary-row{grid-template-columns:2fr .7fr 1fr auto;padding:9px 0;border-top:1px solid var(--line);font-size:13px}.recipe-summary-row strong{overflow-wrap:anywhere}.primary-tag{font-weight:700;color:var(--green)}
@media(max-width:620px){.recipe-toolbar{flex-wrap:wrap}.recipe-column-head{display:none}.recipe-item-row{grid-template-columns:minmax(0,1fr) minmax(90px,.7fr);padding:12px 0}.recipe-item-row :deep(.search-picker){grid-column:1/-1}.recipe-item-row .recipe-category{grid-column:1}.recipe-item-row .field{grid-column:2}.recipe-item-row .primary-choice{grid-column:1}.recipe-item-row button{grid-column:2;justify-self:end}.recipe-summary-row{grid-template-columns:1fr auto}.recipe-manager footer{flex-wrap:wrap}}
</style>
