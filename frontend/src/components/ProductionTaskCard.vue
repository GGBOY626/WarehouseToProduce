<script setup lang="ts">
import { computed } from 'vue';
import type { ProductionTask, ProductionTaskStatus } from '../types';
import { taskWarnings } from '../utils/taskAttention';
import FinishedReceiptSummary from './FinishedReceiptSummary.vue';
import { taskIssuedMaterials } from '../utils/taskIssuedMaterials';

const props = defineProps<{ task: ProductionTask; expanded: boolean }>();
const emit = defineEmits<{ toggle: [] }>();
const labels: Record<ProductionTaskStatus, string> = {
  PREPARING: '进行中', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消',
};
const shortages = computed(() => props.task.targets.filter(t => t.materialTarget && t.shortageQuantity > 0));
const visibleShortages = computed(() => props.expanded ? shortages.value : shortages.value.slice(0, 3));
const warnings = computed(() => taskWarnings(props.task));
// Keep the list's existing eligibility and precision for production progress.
const productionPercent = computed(() => props.task.output?.quantityBasis ? props.task.output.productionPercent : null);
const issued = computed(() => taskIssuedMaterials(props.task));
</script>

<template>
  <article class="task-card" :aria-labelledby="`task-title-${task.id}`">
    <RouterLink :to="`/production-tasks/${task.id}`" class="task-card-link" :aria-label="`${task.name}，查看任务详情`" />
    <header class="task-heading">
      <h3 :id="`task-title-${task.id}`">
        {{ task.name }}
      </h3>
      <div class="task-heading-end">
        <span class="task-status" :class="{ completed: task.status === 'COMPLETED' }">{{ labels[task.status] }}</span>
        <svg class="task-arrow" aria-hidden="true" viewBox="0 0 24 24" width="18" height="18"><path d="m9 5 7 7-7 7" /></svg>
      </div>
    </header>
    <p class="task-dates">开始日期 {{ task.startDate || '暂无发料记录' }}<template v-if="task.status==='COMPLETED'"> · 结束日期 {{ task.endDate || '暂无成品回仓记录' }}</template></p>

    <div class="task-progress-pair">
      <div class="task-progress">
        <div class="progress-label"><span>备料进度</span><strong>{{ task.progressPercent }}<small>%</small></strong></div>
        <div class="progress-track" role="progressbar" :aria-label="`${task.name}：备料进度`" :aria-valuenow="task.progressPercent" :aria-valuemin="0" :aria-valuemax="100">
          <i :style="{ width: `${task.progressPercent}%` }" />
        </div>
      </div>
      <div class="task-progress production">
        <div class="progress-label"><span>生产完成度</span><strong :class="{ unavailable: productionPercent == null }">{{ productionPercent == null ? '—' : productionPercent }}<small v-if="productionPercent != null">%</small></strong></div>
        <div class="progress-track" role="progressbar" :aria-label="`${task.name}：生产完成度`" :aria-valuenow="productionPercent ?? undefined" :aria-valuetext="productionPercent == null ? '暂无可计算数据' : undefined" :aria-valuemin="0" :aria-valuemax="100">
          <i :style="{ width: `${productionPercent || 0}%` }" />
        </div>
      </div>
    </div>

    <FinishedReceiptSummary :task="task" />
    <section class="issued-panel" :aria-labelledby="`issued-title-${task.id}`">
      <h4 :id="`issued-title-${task.id}`">已发往车间 <strong>{{ issued.cartons.toLocaleString() }} 箱</strong></h4>
      <ul v-if="issued.materials.length" class="issued-list">
        <li v-for="item in issued.materials" :key="item.productId">
          <span>{{ item.name }}</span>
          <b>{{ item.cartons.toLocaleString() }} 箱 · {{ item.quantity.toLocaleString() }} {{ item.unit }}<small v-if="item.unknownQuantityCount">（另有数量待确认）</small></b>
        </li>
      </ul>
      <p v-else class="issued-empty">暂无关联物料</p>
    </section>
    <section v-if="shortages.length" class="shortage-panel" :aria-labelledby="`shortage-title-${task.id}`">
      <div class="shortage-heading">
        <h4 :id="`shortage-title-${task.id}`"><svg aria-hidden="true" viewBox="0 0 20 20" width="16" height="16"><path d="M10 2 1.5 17h17L10 2Z" /><path d="M10 7v4m0 2.5v.5" /></svg>待备料物料 <span>{{ shortages.length }} 项</span></h4>
        <span class="shortage-caption">按当前备料缺口</span>
      </div>
      <ul :id="`shortage-list-${task.id}`" class="shortage-list">
        <li v-for="(item, index) in visibleShortages" :key="`${item.productId}-${item.materialBatch ?? ''}-${index}`" class="shortage-item">
          <span class="material-name">{{ item.productName }}</span>
          <div class="material-amounts">
            <span class="missing-quantity">缺 <strong>{{ item.shortageQuantity.toLocaleString() }}</strong> {{ item.baseUnit || '个' }}</span>
            <template v-if="item.shortageCartonSize">
              <span class="cartons-needed">还需 <strong>{{ Math.ceil(item.shortageQuantity / item.shortageCartonSize) }}</strong> 箱</span>
              <span class="carton-size">每箱 {{ item.shortageCartonSize.toLocaleString() }} {{ item.baseUnit || '个' }}</span>
            </template>
          </div>
        </li>
      </ul>
      <button v-if="shortages.length > 3" type="button" class="shortage-toggle" :aria-expanded="expanded" :aria-controls="`shortage-list-${task.id}`" :aria-label="`${task.name}：${expanded ? '收起待备料物料' : `查看其余 ${shortages.length - 3} 项待备料物料`}`" @click.stop="emit('toggle')">
        {{ expanded ? '收起物料列表' : `查看其余 ${shortages.length - 3} 项` }}
        <svg :class="{ expanded }" aria-hidden="true" viewBox="0 0 20 20" width="14" height="14"><path d="m5 7 5 5 5-5" /></svg>
      </button>
    </section>
    <ul v-if="warnings.length" class="task-warnings" aria-label="其他需关注事项"><li v-for="warning in warnings" :key="warning"><span aria-hidden="true">!</span>{{ warning }}</li></ul>
  </article>
</template>

<style scoped>
.task-card{position:relative;isolation:isolate;min-width:0;padding:20px 22px;background:var(--panel);border:1px solid var(--line);border-radius:var(--radius);box-shadow:0 2px 5px rgba(20,39,50,.035)}
.task-card:hover{border-color:#b9c5ca;box-shadow:0 4px 12px rgba(20,39,50,.07)}
.task-card:active{border-color:var(--blue2)}
.task-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px}
.task-dates{margin:8px 0 14px;color:var(--muted);font-size:12px}
.task-heading h3{margin:0;min-width:0;font-family:"Microsoft YaHei UI","PingFang SC",sans-serif;font-size:18px;font-weight:700;line-height:1.5;overflow-wrap:anywhere;text-wrap:pretty}
.task-card-link{position:absolute;inset:0;z-index:1;border-radius:var(--radius);touch-action:manipulation}
.task-card-link:focus-visible{outline:3px solid var(--blue2);outline-offset:3px}
.task-heading-end{display:flex;align-items:center;gap:14px;flex-shrink:0;padding-top:2px}
.task-status{padding:4px 10px;border-radius:6px;font-size:12px;font-weight:650;line-height:1.5;color:var(--blue);background:var(--soft-blue);white-space:nowrap}
.task-status.completed{color:var(--green);background:var(--soft-green)}
.task-arrow{color:var(--muted)}
svg{fill:none;stroke:currentColor;stroke-width:1.7;stroke-linecap:round;stroke-linejoin:round;flex-shrink:0}
.task-progress-pair{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:32px;margin-top:18px}
.progress-label{display:flex;align-items:baseline;justify-content:space-between;gap:8px;margin-bottom:9px;font-size:13px;color:var(--muted)}
.progress-label strong{color:var(--green);font-family:Consolas,"SFMono-Regular",monospace;font-size:22px;font-weight:700;font-variant-numeric:tabular-nums;line-height:1.1;white-space:nowrap}
.progress-label small{font-size:13px;margin-left:2px;font-weight:500}
.production .progress-label strong{color:var(--blue)}
.production .progress-label .unavailable{color:var(--muted)}
.progress-track{height:7px;background:#e5ecee;border-radius:4px;overflow:hidden}
.progress-track i{display:block;height:100%;background:var(--green);border-radius:inherit}
.production .progress-track i{background:var(--blue2)}
.issued-panel{margin-top:18px;padding:12px 14px;background:#f3f7f8;border:1px solid var(--line);border-radius:8px}
.issued-panel h4{display:flex;justify-content:space-between;gap:10px;margin:0 0 8px;font-size:13px;font-weight:650}
.issued-panel h4 strong{color:var(--blue);white-space:nowrap;font-variant-numeric:tabular-nums}
.issued-list{list-style:none;margin:0;padding:0}.issued-list li{display:flex;justify-content:space-between;gap:8px;padding:7px 0;border-top:1px solid var(--line);font-size:12px;line-height:1.5}.issued-list li span{overflow-wrap:anywhere}.issued-list b{text-align:right;font-weight:650;font-variant-numeric:tabular-nums}.issued-list small{font-weight:400;color:var(--orange)}.issued-empty{margin:0;color:var(--muted);font-size:12px}
.shortage-panel{margin-top:20px;background:#f7f8f9;border:1px solid var(--line);border-radius:8px;padding:12px 14px 0}
.shortage-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:10px}
.shortage-heading h4{display:flex;align-items:center;flex-wrap:wrap;gap:7px;font-size:12px;font-weight:650;margin:0;line-height:1.5}
.shortage-heading svg{color:var(--orange)}
.shortage-heading h4 span{font-weight:500;color:var(--muted);padding-left:3px}
.shortage-caption{font-size:11px;color:var(--muted)}
.shortage-list{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));list-style:none;padding:0;margin:0;column-gap:24px}
.shortage-item{min-width:0;padding:10px 0 13px;border-top:1px solid var(--line);font-size:13px;line-height:1.65;overflow-wrap:anywhere}
.shortage-item:only-child{grid-column:1/-1;display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1.15fr);gap:16px}
.shortage-item:only-child .material-amounts{justify-content:flex-end;text-align:right}
.material-name{display:block;font-weight:650;margin-bottom:4px}
.material-amounts{display:flex;align-items:baseline;flex-wrap:wrap;gap:2px 12px}
.missing-quantity{color:var(--muted)}
.missing-quantity strong{font-weight:650;color:var(--ink);font-variant-numeric:tabular-nums}
.cartons-needed{color:var(--orange)}
.cartons-needed strong{font-size:16px;font-variant-numeric:tabular-nums}
.carton-size{flex-basis:100%;font-size:11px;color:var(--muted)}
.shortage-toggle{position:relative;z-index:2;display:flex;align-items:center;justify-content:center;gap:6px;width:100%;min-height:40px;border:0;border-top:1px solid var(--line);background:transparent;color:var(--blue);font-size:12px;font-weight:650;cursor:pointer;border-radius:0 0 6px 6px}
.shortage-toggle:hover{background:var(--soft-blue)}
.shortage-toggle:active{background:var(--line)}
.shortage-toggle:focus-visible{outline:2px solid var(--blue2);outline-offset:2px}
.shortage-toggle svg.expanded{transform:rotate(180deg)}
.task-warnings{display:flex;flex-wrap:wrap;gap:8px 16px;list-style:none;padding:0;margin:14px 0 0;font-size:12px;color:var(--orange)}
.task-warnings li{display:flex;align-items:center;gap:6px}
.task-warnings li span{display:inline-grid;place-items:center;width:15px;height:15px;border:1px solid currentColor;border-radius:50%;font-size:10px;font-weight:700}
@media(max-width:700px){
  .task-card{padding:16px}.task-heading{gap:10px}.task-heading h3{font-size:16px}.task-heading-end{gap:8px}.task-arrow{display:none}
  .task-progress-pair{gap:20px;margin-top:16px}.progress-label{font-size:12px}.progress-label strong{font-size:20px}
  .issued-panel{margin-top:16px;padding:10px 12px}.issued-list li{flex-wrap:wrap}.issued-list b{text-align:left}
  .shortage-panel{margin-top:16px;padding:10px 12px 0}.shortage-caption{display:none}.shortage-list{grid-template-columns:minmax(0,1fr)}
  .shortage-item{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1.15fr);gap:10px;padding:9px 0}
  .material-name{margin:0}.material-amounts{justify-content:flex-end;text-align:right;gap:1px 10px}.carton-size{font-size:11px}
  .shortage-toggle{min-height:44px}
}
@media(max-width:360px){.task-progress-pair{grid-template-columns:minmax(0,1fr);gap:14px}}
</style>
