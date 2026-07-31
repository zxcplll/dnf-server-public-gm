<script setup lang="ts">
import { computed, ref } from 'vue';

type Point = { label: string; value: number };
type Series = { key: string; label: string; color: string; points: Point[] };
const props = withDefaults(defineProps<{ series: Series[]; height?: number }>(), { height: 260 });

const width = 900;
const left = 48;
const right = 18;
const top = 18;
const bottom = 38;
const hover = ref<{ series: Series; point: Point; x: number; y: number } | null>(null);
const values = computed(() => props.series.flatMap((row) => row.points.map((point) => Number(point.value || 0))));
const maxValue = computed(() => Math.max(1, ...values.value));
const allLabels = computed(() => props.series[0]?.points.map((point) => point.label) || []);
const plotHeight = computed(() => props.height - top - bottom);
const plotWidth = width - left - right;
const ticks = computed(() => [0, .25, .5, .75, 1].map((ratio) => Math.round(maxValue.value * ratio)));
const yFor = (value: number) => top + plotHeight.value - (Number(value || 0) / maxValue.value) * plotHeight.value;
const xFor = (index: number, count: number) => count <= 1 ? left + plotWidth / 2 : left + (index / (count - 1)) * plotWidth;
const pointsFor = (series: Series) => series.points.map((point, index) => `${xFor(index, series.points.length)},${yFor(point.value)}`).join(' ');
const showHover = (series: Series, point: Point, index: number) => { hover.value = { series, point, x: xFor(index, series.points.length), y: yFor(point.value) }; };
const compact = (value: number) => new Intl.NumberFormat('zh-CN', { notation: value >= 10000 ? 'compact' : 'standard', maximumFractionDigits: 1 }).format(value);
</script>

<template>
  <div class="trend-chart" @mouseleave="hover = null">
    <div class="chart-legend"><span v-for="row in series" :key="row.key"><i :style="{ background: row.color }"></i>{{ row.label }}</span></div>
    <svg :viewBox="`0 0 ${width} ${height}`" role="img" aria-label="运营趋势图">
      <g class="grid">
        <template v-for="tick in ticks" :key="tick">
          <line :x1="left" :x2="width - right" :y1="yFor(tick)" :y2="yFor(tick)" />
          <text :x="left - 8" :y="yFor(tick) + 4" text-anchor="end">{{ compact(tick) }}</text>
        </template>
      </g>
      <g v-for="row in series" :key="row.key">
        <polyline v-if="row.points.length" class="series-line" :stroke="row.color" :points="pointsFor(row)" />
        <circle v-for="(point, index) in row.points" :key="`${row.key}-${point.label}`" class="series-point" :fill="row.color" :cx="xFor(index, row.points.length)" :cy="yFor(point.value)" r="3.5" @mouseenter="showHover(row, point, index)" />
      </g>
      <text class="axis-label" :x="left" :y="height - 10">{{ allLabels[0] || '' }}</text>
      <text class="axis-label" :x="width - right" :y="height - 10" text-anchor="end">{{ allLabels[allLabels.length - 1] || '' }}</text>
    </svg>
    <div v-if="hover" class="chart-tooltip" :style="{ left: `${Math.min(92, Math.max(8, hover.x / width * 100))}%`, top: `${Math.max(12, hover.y / height * 100)}%` }">
      <strong>{{ hover.series.label }}</strong><span>{{ hover.point.label }}</span><b>{{ compact(hover.point.value) }}</b>
    </div>
    <a-empty v-if="!series.some((row) => row.points.length)" description="当前筛选范围暂无趋势数据" />
  </div>
</template>

<style scoped>
.trend-chart { position: relative; min-width: 0; min-height: 260px; overflow: hidden; border: 1px solid var(--gm-rule); border-radius: 6px; background: rgba(8, 13, 22, .32); }
.trend-chart svg { display: block; width: 100%; height: auto; min-height: 230px; }
.chart-legend { display: flex; flex-wrap: wrap; gap: 12px; padding: 10px 12px 0; color: var(--gm-muted); font-size: 11px; }
.chart-legend span { display: inline-flex; align-items: center; gap: 5px; }
.chart-legend i { width: 16px; height: 2px; }
.grid line { stroke: var(--gm-rule); stroke-dasharray: 3 4; }
.grid text, .axis-label { fill: var(--gm-subtle); font-size: 11px; }
.series-line { fill: none; stroke-width: 2.5; stroke-linecap: round; stroke-linejoin: round; }
.series-point { stroke: var(--gm-surface); stroke-width: 1.5; cursor: crosshair; }
.chart-tooltip { position: absolute; z-index: 2; display: flex; flex-direction: column; min-width: 132px; gap: 2px; padding: 8px 10px; border: 1px solid rgba(77, 228, 210, .38); border-radius: 6px; color: var(--gm-muted); background: #0b1422; box-shadow: 0 12px 30px rgba(0, 0, 0, .34); font-size: 11px; pointer-events: none; transform: translate(-50%, -100%); }
.chart-tooltip strong, .chart-tooltip b { color: var(--gm-text); }
.chart-tooltip b { font-size: 15px; }
.trend-chart :deep(.arco-empty) { position: absolute; inset: 60px 0 0; display: flex; align-items: center; justify-content: center; }
</style>
