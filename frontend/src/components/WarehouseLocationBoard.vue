<script setup lang="ts">
import { computed } from 'vue';
import { areaLanes, laneLabels } from '../utils/warehouse';
import type { WarehouseFilters } from '../utils/warehouse';
import type { WarehouseOverview } from '../types/warehouse';
const props=defineProps<{data:WarehouseOverview;filters:WarehouseFilters;loading:boolean}>();
const emit=defineEmits<{select:[id:number,lane?:string];clear:[]}>();
const racks=computed(()=>props.data.areas.filter(a=>a.kind==='RACK'));
const aisles=computed(()=>props.data.areas.filter(a=>a.kind==='AISLE'));
const expandedAisles=computed(()=>aisles.value.filter(a=>props.filters.row ? a.rackRow===props.filters.row : a.id===props.filters.areaId));
const cells=computed(()=>{
  const map=new Map<string,{count:number;names:Set<string>}>();
  for(const p of props.data.pallets)for(const key of [String(p.areaId),`${p.areaId}/${p.lane}`]){
    const cell=map.get(key)||{count:0,names:new Set<string>()};
    cell.count++;p.items.forEach(i=>cell.names.add(i.productName));map.set(key,cell);
  }
  return map;
});
function count(id:number,lane=''){return cells.value.get(lane?`${id}/${lane}`:String(id))?.count||0}
function description(id:number,lane=''){
  const area=props.data.areas.find(a=>a.id===id);
  const cell=cells.value.get(lane?`${id}/${lane}`:String(id));
  const names=cell?[...cell.names]:[];
  return `${area?.name}${area?.kind==='RACK'?' 排':''} ${laneLabels[lane]||'全部位置'} · ${cell?.count||0} 托${names.length?' · '+names.slice(0,3).join('、')+(names.length>3?'等':''): ' · 尚无已录入托盘'}`;
}
</script>

<template>
  <section class="location-board card" aria-labelledby="warehouse-map-title" :aria-busy="loading">
    <header class="board-heading">
      <div><h2 id="warehouse-map-title">货位看板</h2><p>点击排号或层位筛选 · 数字代表托盘数，不是物料件数</p></div>
      <button v-if="filters.areaId||filters.row||filters.kind" class="btn btn-secondary" @click="emit('clear')">取消位置筛选</button>
      <a class="board-link" href="#stock-list-title">{{filters.areaId||filters.row||filters.kind?'查看筛选结果 ↓':'查看托盘列表 ↓'}}</a>
    </header>
      <div class="map-legend"><span><i class="empty-key"/>未录入</span><span><i class="occupied-key"/>有托盘</span><span><i class="selected-key"/>已选位置</span><span class="mobile-scroll-hint">左右滑动查看 AA–MM</span></div>
    <p v-if="loading&&!data.areas.length" class="empty" role="status">正在读取货位…</p>
    <div v-else class="rack-scroll" tabindex="0" role="region" aria-label="AA 至 MM 货架总览，可横向滚动">
      <div class="rack-map">
        <div class="level-key" aria-hidden="true"><span>货架</span><span>上</span><span>中</span><span>下</span></div>
        <div v-for="rack in racks" :key="rack.id" class="rack-column" :class="{selected:filters.row===rack.name}">
          <button class="rack-letter" :title="description(rack.id)" :aria-label="description(rack.id)" :aria-pressed="filters.areaId===rack.id&&!filters.lane" @click="emit('select',rack.id)">{{rack.name}}</button>
          <button v-for="lane in areaLanes(rack)" :key="lane" class="rack-cell" :class="{occupied:count(rack.id,lane)>0}" :aria-pressed="filters.areaId===rack.id&&filters.lane===lane" :title="description(rack.id,lane)" :aria-label="description(rack.id,lane)" @click="emit('select',rack.id,lane)">{{count(rack.id,lane)}}</button>
        </div>
      </div>
    </div>
    <div class="aisle-detail">
      <div class="aisle-heading"><h3>{{filters.row ? `${filters.row} 排旁过道` : '过道 · 选排后显示'}}</h3><div class="aisle-picker"><label for="board-aisle">快速定位</label><select id="board-aisle" name="board-aisle" class="select" :value="aisles.some(a=>a.id===filters.areaId)?filters.areaId:''" @change="($event.target as HTMLSelectElement).value&&emit('select',Number(($event.target as HTMLSelectElement).value))"><option value="">选择过道…</option><option v-for="a in aisles" :key="a.id" :value="a.id">{{a.rackRow?`${a.rackRow} 排旁 · `:''}}{{a.name}}</option></select></div></div>
      <div v-if="expandedAisles.length" class="aisle-map">
        <div v-for="a in expandedAisles" :key="a.id" class="aisle-strip">
          <button class="aisle-title" :aria-pressed="filters.areaId===a.id&&!filters.lane" @click="emit('select',a.id)"><strong>{{a.name}}</strong><span>{{count(a.id)}} 托 · {{a.lanes}} 排</span></button>
          <div class="aisle-lanes"><button v-for="lane in areaLanes(a)" :key="lane" :class="{occupied:count(a.id,lane)>0}" :title="description(a.id,lane)" :aria-label="description(a.id,lane)" :aria-pressed="filters.areaId===a.id&&filters.lane===lane" @click="emit('select',a.id,lane)"><span>{{laneLabels[lane]}}</span><b>{{count(a.id,lane)}}</b></button></div>
        </div>
      </div>
      <p v-else-if="filters.row" class="hint">{{filters.row?'这一排尚未关联过道，可从快速定位选择其他过道。':'选择 A–M 排号查看排旁过道，或从快速定位选择过道。'}}</p>
    </div>
    <p class="map-note">0 表示尚无已录入托盘，不代表已确认空位。过道左右以现场固定朝向为准。</p>
  </section>
</template>

<style scoped>
.location-board{overflow:hidden}.board-heading{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:16px 18px 10px}.board-heading h2{font-size:16px;margin:0}.board-heading p{font-size:12px;color:var(--muted);margin:6px 0 0;line-height:1.6}.board-link{font-size:12px;color:var(--blue);white-space:nowrap;padding:12px 0}.board-heading .btn{font-size:12px;flex-shrink:0}.map-legend{display:flex;align-items:center;flex-wrap:wrap;gap:14px;padding:0 18px 12px;font-size:11px;color:var(--muted)}.map-legend span{display:flex;align-items:center;gap:5px}.map-legend i{width:10px;height:10px;border:1px solid #dce2e5;border-radius:2px}.empty-key{background:#f1f4f5}.occupied-key{background:#d9eaf2!important;border-color:#9bbaca!important}.selected-key{background:var(--blue);border-color:var(--blue)!important}.mobile-scroll-hint{display:none!important}.rack-scroll{position:relative;contain:inline-size;overflow-x:auto;padding:2px 18px 16px;overscroll-behavior-x:contain}.rack-scroll:focus-visible{outline:2px solid var(--blue);outline-offset:-3px}.rack-map{display:grid;grid-template-columns:26px repeat(13,minmax(44px,1fr));gap:5px;min-width:710px}.level-key,.rack-column{display:grid;grid-template-rows:36px repeat(3,42px);gap:4px}.level-key{font-size:11px;color:var(--muted);text-align:center;align-items:center}.rack-column{padding:0 3px 5px;border-inline:2px solid #cfdae0;border-bottom:2px solid #cfdae0;border-radius:4px;background:#f8fafb}.rack-column.selected{border-color:var(--blue)}.rack-letter,.rack-cell{position:relative;border:1px solid transparent;border-radius:4px;cursor:pointer}.rack-letter{background:transparent;font-size:14px;font-weight:750;color:var(--blue)}.rack-cell{background:#eef1f3;color:#637078;font:500 16px Consolas,monospace}.rack-cell.occupied{background:#d9eaf2;border-color:#b4cedb;color:var(--blue);font-weight:750}.rack-letter[aria-pressed=true],.rack-cell[aria-pressed=true],.aisle-lanes button[aria-pressed=true]{background:var(--blue);color:white;border-color:var(--blue);box-shadow:inset 0 0 0 1px white}.aisle-detail{padding:8px 18px;border-top:1px solid var(--line)}.aisle-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.aisle-heading h3{margin:0;font-size:13px;white-space:nowrap}.aisle-picker{display:flex;align-items:center;gap:8px;min-width:0;font-size:12px}.aisle-picker label{white-space:nowrap;color:var(--muted)}.aisle-picker select{max-width:240px;font-size:12px}.aisle-map{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-top:10px}.aisle-strip{min-width:0}.aisle-title{display:flex;align-items:center;justify-content:space-between;gap:8px;width:100%;min-height:40px;padding:0 4px;background:none;border:0;cursor:pointer;text-align:left}.aisle-title strong{font-size:13px;overflow-wrap:anywhere}.aisle-title span{font-size:11px;color:var(--muted);white-space:nowrap}.aisle-title[aria-pressed=true]{color:var(--blue);text-decoration:underline;text-underline-offset:4px}.aisle-lanes{display:flex;gap:6px}.aisle-lanes button{flex:1;display:flex;align-items:center;justify-content:space-between;min-width:0;gap:4px;min-height:44px;border:1px solid #dce2e5;background:#f1f4f5;border-radius:5px;padding:8px;color:#68777e;cursor:pointer;font-size:12px}.aisle-lanes button.occupied{background:#d9eaf2;color:var(--blue);border-color:#b4cedb}.aisle-lanes b{font:700 16px Consolas,monospace}.map-note{margin:0;padding:0 18px 12px;color:var(--muted);font-size:11px;line-height:1.7}.hint{margin:10px 0 0}@media(max-width:760px){.mobile-scroll-hint{display:flex!important;margin-left:auto}.board-heading{align-items:flex-start}.board-heading,.aisle-detail{padding-inline:12px}.map-legend,.rack-scroll{padding-inline:12px}.aisle-map{grid-template-columns:1fr}.aisle-picker label{display:none}}@media(max-width:420px){.board-heading{flex-wrap:wrap}.board-heading .btn,.board-link{min-height:36px;padding:6px 8px}.mobile-scroll-hint{width:100%;margin:0}.aisle-picker{max-width:60%}}
</style>
