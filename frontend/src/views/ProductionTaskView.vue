<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { productionTasksApi, productsApi } from "../api";
import { errorMessage } from "../api/http";
import { useAuth } from "../auth";
import type { Product, ProductionTask, ProductionTaskStatus } from "../types";
import SearchPicker from "../components/SearchPicker.vue";
import { today } from "../utils/time";
const { authenticated } = useAuth();
const tasks=ref<ProductionTask[]>([]),message=ref(""),showForm=ref(false),saving=ref(false);
const product=ref<Product>();
const fresh=()=>({productId:undefined as number|undefined,targetQuantity:0,plannedDate:today(),batchNo:"",remarks:""});
const form=ref(fresh());
const active=computed(()=>tasks.value.filter(t=>t.status==='PREPARING'||t.status==='IN_PROGRESS'));
const finished=computed(()=>tasks.value.filter(t=>t.status==='COMPLETED'||t.status==='CANCELLED'));
const labels:Record<ProductionTaskStatus,string>={PREPARING:'备料中',IN_PROGRESS:'生产中',COMPLETED:'已完成',CANCELLED:'已取消'};
async function load(){try{tasks.value=await productionTasksApi.list()}catch(e){message.value=errorMessage(e)}}
function selectProduct(value?:Product){product.value=value;form.value.productId=value?.id}
async function save(){if(!form.value.productId||form.value.targetQuantity<=0||!form.value.batchNo.trim()){message.value='请填写目标产品、目标数量和生产批次。';return}saving.value=true;message.value='';try{await productionTasksApi.create(form.value);showForm.value=false;form.value=fresh();product.value=undefined;await load()}catch(e){message.value=errorMessage(e)}finally{saving.value=false}}
onMounted(load);
</script>
<template><div class="page"><div class="page-heading"><div><h1 class="page-title">生产任务</h1><p class="page-lead">用目标数量串联发料、退料和成品入库。</p></div><button v-if="authenticated" class="btn btn-primary" @click="showForm=!showForm">{{showForm?'收起':'＋ 新建任务'}}</button></div><p v-if="message" class="form-alert">{{message}}</p>
<section v-if="showForm" class="card card-pad task-form"><SearchPicker v-model="form.productId" label="目标产品 *" placeholder="搜索生产成品" :load="q=>productsApi.search(q,false,'PRODUCTION_TO_WAREHOUSE')" @select="selectProduct"/><div class="grid-2"><label class="field"><span>目标数量 *</span><input class="input" type="number" min="1" v-model.number="form.targetQuantity"/></label><label class="field"><span>计划日期 *</span><input class="input" type="date" v-model="form.plannedDate"/></label></div><label class="field"><span>生产批次 *</span><input class="input mono" maxlength="100" v-model.trim="form.batchNo"/></label><label class="field"><span>备注</span><textarea class="textarea" v-model.trim="form.remarks"/></label><button class="btn btn-primary" :disabled="saving" @click="save">{{saving?'保存中…':'创建生产任务'}}</button></section>
<section class="section"><div class="section-head"><h2 class="section-title">进行中的任务</h2><span class="hint">{{active.length}} 个</span></div><div v-if="!active.length" class="card empty">暂无进行中的生产任务。</div><div v-else class="task-list"><RouterLink v-for="task in active" :key="task.id" :to="`/production-tasks/${task.id}`" class="task-card card"><div><span class="task-status">{{labels[task.status]}}</span><strong>{{task.productName}}</strong><small>{{task.batchNo}} · 计划 {{task.plannedDate}}</small></div><div class="target"><b>{{task.completedQuantity.toLocaleString()}} / {{task.targetQuantity.toLocaleString()}}</b><span>{{task.baseUnit||'个'}} · {{task.progressPercent}}%</span></div><div class="progress"><i :style="{width:`${task.progressPercent}%`}"/></div></RouterLink></div></section>
<section v-if="finished.length" class="section"><div class="section-head"><h2 class="section-title">已结束</h2><span class="hint">{{finished.length}} 个</span></div><div class="task-list"><RouterLink v-for="task in finished" :key="task.id" :to="`/production-tasks/${task.id}`" class="task-card card finished"><div><span class="task-status">{{labels[task.status]}}</span><strong>{{task.productName}}</strong><small>{{task.batchNo}}</small></div><div class="target"><b>{{task.completedQuantity.toLocaleString()}} / {{task.targetQuantity.toLocaleString()}}</b><span>{{task.baseUnit||'个'}}</span></div></RouterLink></div></section></div></template>
<style scoped>.page-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}.task-form{display:grid;gap:14px;margin-top:16px}.task-form>.btn{justify-self:start}.task-list{display:grid;gap:10px}.task-card{position:relative;display:grid;grid-template-columns:minmax(0,1fr) auto;gap:10px;padding:14px 16px;overflow:hidden}.task-card>div:first-child{display:grid;gap:4px}.task-card strong{font-size:17px}.task-card small,.target span{color:#66747c;font-size:12px}.task-status{width:max-content;color:#174a68;font-size:11px;font-weight:750}.target{display:grid;text-align:right;align-content:center}.target b{font-variant-numeric:tabular-nums}.progress{position:absolute;left:0;right:0;bottom:0;height:4px;background:#e5ecee}.progress i{display:block;height:100%;background:#267566}.finished{opacity:.72}.form-alert{color:#8f1d25}</style>
