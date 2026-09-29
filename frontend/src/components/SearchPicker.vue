<script setup lang="ts" generic="T extends {id:number,name:string}">
import {ref,shallowRef,watch} from 'vue';
const props=defineProps<{modelValue?:number;label:string;placeholder:string;load:(q:string)=>Promise<T[]>;display?:(item:T)=>string}>();
const emit=defineEmits<{(e:'update:modelValue',value:number|undefined):void;(e:'select',value:T|undefined):void}>();
const query=ref('');const results=shallowRef<T[]>([]);const open=ref(false);let timer:number|undefined;
watch(()=>props.modelValue,async id=>{if(id&&!query.value){const all=await props.load('');const selected=all.find(x=>x.id===id);if(selected)query.value=props.display?.(selected)||selected.name;}},{immediate:true});
function search(){open.value=true;window.clearTimeout(timer);timer=window.setTimeout(async()=>results.value=await props.load(query.value),180)}
function choose(item:T){query.value=props.display?.(item)||item.name;emit('update:modelValue',item.id);emit('select',item);open.value=false}
function clearIfChanged(){if(!props.modelValue)return;emit('update:modelValue',undefined);emit('select',undefined)}
function closeLater(){window.setTimeout(()=>open.value=false,150)}
</script>
<template><div class="picker"><label class="required">{{label}}</label><input class="input" v-model="query" :aria-label="label" :name="label" :placeholder="placeholder" autocomplete="off" @focus="search" @input="clearIfChanged();search()" @blur="closeLater"/><div v-if="open" class="picker-menu"><button v-for="item in results" :key="item.id" type="button" @mousedown.prevent="choose(item)"><slot :item="item">{{display?.(item)||item.name}}</slot></button><div v-if="!results.length" class="picker-empty">没有找到可选资料</div></div></div></template>
<style scoped>.picker{display:grid;gap:7px;position:relative}.picker label{font-size:14px;font-weight:680}.picker-menu{position:absolute;top:78px;left:0;right:0;background:#fff;border:1px solid #b9c5ca;border-radius:9px;box-shadow:0 10px 30px rgba(23,33,38,.16);max-height:240px;overflow:auto;z-index:45}.picker-menu button{display:block;width:100%;min-height:48px;text-align:left;background:#fff;border:0;border-bottom:1px solid #edf0f2;padding:9px 12px}.picker-menu button:active{background:#e9f2f6}.picker-empty{padding:16px;color:#66747c;font-size:13px}</style>
