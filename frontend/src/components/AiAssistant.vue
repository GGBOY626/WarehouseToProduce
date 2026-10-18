<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { askAi, type AiScope, type AiReply } from '../api/ai';
import { aiAmount, aiRecordLink, aiScopeLabel } from '../utils/ai';
import { errorMessage } from '../api/http';
import { showDateTime } from '../utils/time';
import { useAuth } from '../auth';
const props=defineProps<{scope:AiScope}>();
const {authenticated}=useAuth();
const dialog=ref<HTMLDialogElement>(),input=ref<HTMLTextAreaElement>(),log=ref<HTMLElement>();
const question=ref(''),busy=ref(false),error=ref('');
const messages=ref<{question:string;reply?:AiReply}[]>([]);
let controller:AbortController|undefined;
const scopeLabel=computed(()=>aiScopeLabel(props.scope));
const quick=computed(()=>props.scope.taskId?['这个任务为什么还没完成？','当前备料还缺什么？','为什么完成度是这个百分比？','哪些记录需要核对？']:['关节饮有哪些批次？','当前范围有哪些托盘？','哪些物料待盘点？']);
function clear(){controller?.abort();controller=undefined;busy.value=false;messages.value=[];error.value='';question.value=''}
watch(()=>JSON.stringify(props.scope),clear);
watch(authenticated,value=>{if(!value){clear();dialog.value?.close()}});
onBeforeUnmount(clear);
async function open(summary=false){dialog.value?.showModal();await nextTick();if(window.matchMedia('(pointer:fine)').matches)input.value?.focus();if(summary)await send(props.scope.taskId?'总结当前任务的备料、回仓和需要核对的问题。':'总结当前范围的物料、批次、货位和待盘点情况。')}
async function send(text=question.value){
  if(busy.value||!text.trim()||!authenticated.value)return;
  const current=new AbortController();controller=current;busy.value=true;error.value='';question.value='';
  const message={question:text.trim()} as {question:string;reply?:AiReply};messages.value.push(message);
  // Questions are independent snapshots: old business values are never reused as live inventory.
  try{const reply=await askAi(message.question,{...props.scope},current.signal);if(controller===current)messages.value[messages.value.length-1]!.reply=reply}
  catch(e){if(!current.signal.aborted)error.value=errorMessage(e)}
  finally{if(controller===current){busy.value=false;controller=undefined;await nextTick();log.value?.querySelector('.ai-turn:last-of-type')?.scrollIntoView({block:'start'})}}
}
</script>
<template>
  <div v-if="authenticated" class="ai-launch">
    <button class="btn btn-secondary" @click="open()">{{scope.taskId?'AI 分析':'问仓库'}}</button>
    <button class="btn btn-ghost" @click="open(true)">{{scope.taskId?'任务总结':'总结当前范围'}}</button>
  </div>
  <dialog ref="dialog" class="ai-drawer" aria-labelledby="ai-title" @close="controller?.abort()">
    <header class="ai-heading"><div><span class="hint">只读业务助手</span><h2 id="ai-title">{{scope.taskId?'生产任务分析':'问仓库'}}</h2></div><button class="btn btn-secondary" @click="dialog?.close()">关闭</button></header>
    <div class="ai-scope"><strong>查询范围</strong><p>{{scopeLabel}}</p><small>跟随页面筛选。查询全仓请关闭此窗口并清除页面筛选。</small></div>
    <div class="ai-quick"><button v-for="q in quick" :key="q" :disabled="busy" @click="send(q)">{{q}}</button></div>
    <div ref="log" class="ai-log" role="log" aria-live="polite" :aria-busy="busy">
      <p v-if="!messages.length" class="hint">输入物料、批次或业务问题。数量以查询时的已录入记录为准，每次提问重新查询。</p>
      <article v-for="(message,index) in messages" :key="index" class="ai-turn">
        <p class="ai-question">{{message.question}}</p>
        <template v-if="message.reply">
          <p class="ai-answer">{{message.reply.answer}}</p>
          <section v-for="(result,ri) in message.reply.results" :key="ri" class="ai-result">
            <p class="ai-source">{{result.source}}<br>{{showDateTime(result.queriedAt.replace(/\[.*\]$/,''))}} · 奥克兰时间</p>
            <template v-if="result.inventory">
              <p class="ai-count">{{result.inventory.materialCount}} 种物料 · {{result.inventory.palletCount}} 托 · {{result.inventory.pendingPalletCount}} 托待盘点</p>
              <p v-if="result.inventory.detailsOmitted" class="hint">总项数覆盖完整范围；明细过多，请指定物料、批次或缩小位置范围。</p>
              <p v-else-if="!result.inventory.materials.length" class="hint">当前条件未查到已录入物料，请核对范围或关键词。</p>
              <article v-for="(material,mi) in result.inventory.materials" :key="mi" class="ai-material">
                <h3>{{material.name}} <small>{{material.code||'编码未录入'}}{{material.productId?` · 产品 #${material.productId}`:''}}</small></h3>
                <strong class="mono">{{aiAmount(material)}}</strong>
                <p class="hint">{{material.palletIds.length}} 托<template v-if="material.pending"> · {{material.pending}} 项待盘点</template><template v-if="material.unitUnknown"> · {{material.unitUnknown}} 项单位未录入</template></p>
                <details v-for="(batch,bi) in material.batches" :key="bi" class="ai-batch">
                  <summary><span>{{batch.missing?'': '批次 '}}{{batch.label}}</span><strong>{{aiAmount(batch)}}</strong><span v-if="batch.pending" class="status warn">{{batch.pending}} 项待盘点</span></summary>
                  <div v-for="(location,li) in batch.locations" :key="li" class="ai-location"><b>{{location.location}}</b><p>{{aiAmount(location)}}<span v-if="location.pending"> · {{location.pending}} 项待盘点</span><span v-if="location.unitUnknown"> · 单位待确认</span></p><p v-if="location.rawBatches.length" class="hint">原料批号：{{location.rawBatches.join('、')}}</p><RouterLink v-for="palletId in location.palletIds" :key="palletId" class="link" :to="aiRecordLink('pallet',palletId)" @click="dialog?.close()">查看托盘 #{{palletId}} ↗ </RouterLink></div>
                </details>
              </article>
            </template>
            <template v-if="result.task">
              <RouterLink class="link" :to="aiRecordLink('task',result.task.id)" @click="dialog?.close()">{{result.task.name}} · 查看任务 ↗</RouterLink>
              <p>备料进度 {{result.task.progressPercent}}%</p>
              <article v-for="t in result.task.targets" :key="t.productId" class="ai-material"><h3>{{t.productName}} <small>{{t.materialCode}}</small></h3><p>计划需求 {{t.targetQuantity}} {{t.baseUnit}}</p><p>发料 {{t.issuedQuantity}} / 退料 {{t.returnedQuantity}} / 净发料 {{t.completedQuantity}} {{t.baseUnit}}</p><p>欠料 {{t.shortageQuantity}} {{t.baseUnit}}<span v-if="t.unknownQuantityCount"> · {{t.unknownQuantityCount}} 项数量待确认</span></p></article>
              <article v-if="result.task.output" class="ai-material"><h3>{{result.task.output.productName}} · 成品</h3><p>已知回仓 {{result.task.output.receivedQuantity}} {{result.task.output.baseUnit}}</p><p v-if="result.task.output.producedQuantity!=null">回仓折算主料 {{result.task.output.producedQuantity}} / 主料净发料 {{result.task.output.netIssuedQuantity??'待确认'}}</p><p>生产完成度 {{result.task.output.productionPercent==null?'待确认':`${result.task.output.productionPercent}%`}}</p><p>{{result.task.output.provisional?'暂估损耗率':'损耗率（需核对）'}} {{result.task.output.lossPercent==null?'待确认':`${result.task.output.lossPercent}%`}}</p><p v-if="result.task.output.unknownQuantityCount">另有 {{result.task.output.unknownQuantityCount}} 项数量待确认</p></article>
              <details><summary>核对原始流转记录</summary><RouterLink v-for="m in result.task.movements" :key="m.id" class="ai-record link" :to="aiRecordLink('movement',m.id)" @click="dialog?.close()">{{m.recordNo}} · {{m.returnMovement?'退料':m.direction==='WAREHOUSE_TO_PRODUCTION'?'发料':'回仓'}}{{m.status!=='ACTIVE'?' · 已作废，不计入统计':''}}{{m.unknownQuantityCount?' · 数量待核对':''}} ↗</RouterLink></details>
            </template>
          </section>
        </template>
      </article>
      <p v-if="busy" role="status">正在核对业务记录…</p>
      <p v-if="error" class="error-text" role="alert">{{error}}</p>
    </div>
    <form class="ai-compose" @submit.prevent="send()"><label for="ai-question">询问当前范围</label><textarea id="ai-question" name="ai-question" autocomplete="off" :spellcheck="false" ref="input" v-model="question" class="textarea" maxlength="2000" placeholder="例如：22344 批分别放在哪里？" :disabled="busy" required/><div><button type="button" class="btn btn-ghost" @click="clear">清空对话</button><button class="btn btn-primary" :disabled="busy||!question.trim()">{{busy?'查询中…':'发送'}}</button></div></form>
  </dialog>
</template>
<style scoped>
:global(body:has(.ai-drawer[open])){overflow:hidden}.ai-heading,.ai-scope,.ai-compose{flex-shrink:0}
.ai-launch{display:flex;gap:8px;flex-wrap:wrap;margin:12px 0}.ai-drawer{position:fixed;inset:0 0 0 auto;margin:0;width:min(560px,100%);max-width:100%;overflow:hidden;height:100dvh;max-height:100dvh;border:0;border-left:1px solid var(--line);padding:0;background:#f3f5f6;color:var(--ink)}.ai-drawer[open]{display:flex;flex-direction:column}.ai-drawer::backdrop{background:rgba(23,33,38,.38)}.ai-heading{display:flex;justify-content:space-between;align-items:center;padding:16px 20px;background:white}.ai-heading h2{font-size:22px;margin:3px 0;font-family:'Microsoft YaHei UI',sans-serif}.ai-scope{border-left:4px solid #267566;background:#e9f5f1;padding:12px 16px}.ai-scope strong{font-size:12px;color:#267566}.ai-scope p{margin:6px 0;font-size:14px}.ai-scope small{font-size:11px;color:#52626b}.ai-quick{display:flex;gap:6px;overflow-x:auto;flex-shrink:0;padding:10px 16px}.ai-quick button{border:1px solid var(--line);border-radius:7px;min-height:40px;background:white;padding:6px 10px;white-space:nowrap;font-size:12px;cursor:pointer}.ai-log{overflow-wrap:anywhere;flex:1;min-height:80px;overflow-y:auto;padding:0 16px 16px;overscroll-behavior:contain}.ai-question{background:#174a68;color:white;padding:12px 14px;border-radius:10px 10px 2px 10px;margin-left:24px;white-space:pre-wrap}.ai-answer{white-space:pre-wrap;line-height:1.75;font-size:13px}.ai-result{border-top:1px solid var(--line);margin-top:12px}.ai-source{font-size:11px;color:var(--muted);line-height:1.7}.ai-count{font-size:13px;font-weight:700}.ai-material{background:white;border:1px solid var(--line);border-radius:10px;padding:12px;margin:10px 0;overflow-wrap:anywhere}.ai-material h3{font-size:15px;margin:0 0 10px}.ai-material h3 small{display:block;font-size:11px;font-weight:400;color:var(--muted);margin-top:4px}.ai-material p{font-size:12px;line-height:1.6}.ai-batch{border-top:1px solid #dce2e5;padding:9px 0}.ai-batch summary{cursor:pointer;font-size:12px;min-height:28px;line-height:1.8}.ai-batch summary strong{margin-left:12px;font-family:Consolas,monospace}.ai-location{border-left:2px solid #174a68;margin:12px 0 0 4px;padding-left:10px;font-size:12px}.ai-location .link{font-size:12px;display:inline-flex;min-height:40px;align-items:center}.ai-record{display:block;padding:12px 0;font-size:12px}.ai-compose{background:white;border-top:1px solid var(--line);padding:12px 16px max(12px,env(safe-area-inset-bottom))}.ai-compose label{font-size:12px;font-weight:700;display:block;margin-bottom:6px}.ai-compose textarea{min-height:64px;max-height:120px}.ai-compose>div{display:flex;justify-content:space-between;gap:8px;margin-top:8px}@media(max-width:420px){.ai-heading{padding:12px 16px}.ai-log{padding:0 12px 12px}.ai-scope{padding:10px 12px}.ai-question{margin-left:12px}}
</style>
