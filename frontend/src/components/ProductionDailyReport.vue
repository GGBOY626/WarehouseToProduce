<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { productionTasksApi } from '../api';
import { errorMessage } from '../api/http';
import { today } from '../utils/time';
import type { ProductionDailyReport } from '../types';

const props=defineProps<{taskId:number;productName:string;authenticated:boolean}>();
const reports=ref<ProductionDailyReport[]>([]),reportDate=ref(today());
const groupA=ref<number|''>(0),groupB=ref<number|''>(0);
const loading=ref(false),saving=ref(false),notice=ref(''),error=ref('');
let request=0;
function selectDate(date:string){
  reportDate.value=date;
  const report=reports.value.find(item=>item.reportDate===date);
  groupA.value=report?.groupACartons??0;groupB.value=report?.groupBCartons??0;
}
async function load(){
  loading.value=true;error.value='';const current=++request;
  try{reports.value=await productionTasksApi.dailyReports(props.taskId);selectDate(reportDate.value)}
  catch(e){if(current===request)error.value=errorMessage(e)}
  finally{if(current===request)loading.value=false}
}
watch(reportDate,date=>{
  const report=reports.value.find(item=>item.reportDate===date);
  groupA.value=report?.groupACartons??0;groupB.value=report?.groupBCartons??0;
});
async function save(){
  if(saving.value||!props.authenticated||groupA.value===''||groupB.value===''||!Number.isSafeInteger(groupA.value)||!Number.isSafeInteger(groupB.value))return;
  saving.value=true;notice.value='';error.value='';
  try{
    const saved=await productionTasksApi.saveDailyReport(props.taskId,{reportDate:reportDate.value,groupACartons:groupA.value,groupBCartons:groupB.value});
    reports.value=[saved,...reports.value.filter(report=>report.reportDate!==saved.reportDate)].sort((a,b)=>b.reportDate.localeCompare(a.reportDate));
    notice.value=`${saved.reportDate} 生产日报已保存`;
  }catch(e){error.value=errorMessage(e)}finally{saving.value=false}
}
onMounted(load);
</script>
<template>
  <section class="daily-report section" aria-labelledby="daily-report-title">
    <header class="section-head"><div><h2 id="daily-report-title" class="section-title">生产日报</h2><p class="hint">{{productName}} · 按实际生产箱数记录</p></div></header>
    <form class="report-form" @submit.prevent="save">
      <div class="field"><label for="daily-report-date">生产日期</label><input id="daily-report-date" v-model="reportDate" class="input" type="date" required :disabled="loading||saving" /></div>
      <div class="field"><label for="daily-report-group-a">A组产量（箱）</label><input id="daily-report-group-a" v-model.number="groupA" class="input mono" type="number" min="0" max="9007199254740991" step="1" inputmode="numeric" required :disabled="loading||saving||!authenticated" /></div>
      <div class="field"><label for="daily-report-group-b">B组产量（箱）</label><input id="daily-report-group-b" v-model.number="groupB" class="input mono" type="number" min="0" max="9007199254740991" step="1" inputmode="numeric" required :disabled="loading||saving||!authenticated" /></div>
      <button v-if="authenticated" class="btn btn-primary" :disabled="loading||saving||groupA===''||groupB===''">{{saving?'正在保存…':'保存日报'}}</button>
    </form>
    <p v-if="notice" class="notice" role="status">{{notice}}</p>
    <p v-if="error" class="error-text" role="alert">{{error}}</p>
    <p v-if="loading" class="hint">正在读取日报…</p>
    <p v-else-if="!reports.length" class="hint">暂无历史日报</p>
    <div v-else class="report-history" aria-label="历史生产日报">
      <div class="history-heading"><span>日期</span><span>A组</span><span>B组</span><span>合计</span></div>
      <button v-for="report in reports" :key="report.id" type="button" class="history-row" :aria-pressed="reportDate===report.reportDate" @click="selectDate(report.reportDate)">
        <time>{{report.reportDate}}</time><span>{{report.groupACartons.toLocaleString()}} 箱</span><span>{{report.groupBCartons.toLocaleString()}} 箱</span><strong>{{report.totalCartons.toLocaleString()}} 箱</strong>
      </button>
    </div>
  </section>
</template>
<style scoped>
.daily-report{display:grid;gap:12px}.daily-report .section-head{align-items:start}.daily-report .section-title{margin:0}.daily-report .hint{margin:5px 0 0}.report-form{display:grid;grid-template-columns:repeat(3,minmax(0,1fr)) auto;gap:10px;align-items:end}.report-form .field{min-width:0}.report-form button{white-space:nowrap}.notice{margin:0;color:var(--green);font-size:13px}.report-history{border-top:1px solid var(--line)}.history-heading,.history-row{display:grid;grid-template-columns:minmax(100px,1.2fr) repeat(3,minmax(80px,1fr));gap:12px;align-items:center}.history-heading{padding:9px 10px;color:var(--muted);font-size:12px}.history-row{width:100%;min-height:42px;padding:8px 10px;border:0;border-top:1px solid var(--line);background:white;text-align:left;font-size:13px;font-variant-numeric:tabular-nums;cursor:pointer}.history-row[aria-pressed=true]{background:#eef5f5}.history-row strong{text-align:right}.history-row span,.history-row time{white-space:nowrap}.history-row:focus-visible{outline:2px solid var(--blue);outline-offset:-2px}@media(max-width:620px){.report-form{grid-template-columns:repeat(2,minmax(0,1fr))}.report-form button{grid-column:1/-1}.history-heading,.history-row{grid-template-columns:minmax(86px,1.1fr) repeat(3,minmax(64px,1fr));gap:5px;padding-inline:5px;font-size:12px}.history-row span,.history-row time{white-space:normal}}
</style>
