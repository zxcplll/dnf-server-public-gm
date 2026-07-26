<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { Message } from '@arco-design/web-vue';
import Request from '../../../api/Request';

type MetricKey = 'cpu' | 'memoryPercent' | 'storagePercent' | 'networkPercent';
type ChartPoint = { x: number; y: number; value: number; timestamp: number; source: any };

const CHART_WIDTH = 760;
const CHART_HEIGHT = 240;
const PLOT_LEFT = 48;
const PLOT_RIGHT = 12;
const PLOT_TOP = 14;
const PLOT_BOTTOM = 34;
const PLOT_HEIGHT = CHART_HEIGHT - PLOT_TOP - PLOT_BOTTOM;
const PLOT_WIDTH = CHART_WIDTH - PLOT_LEFT - PLOT_RIGHT;
const axisTicks = [0, 25, 50, 75, 100];
const charts = [
  { key: 'cpu' as MetricKey, title: 'CPU 使用率', color: '#3c7eff' },
  { key: 'memoryPercent' as MetricKey, title: '内存使用率', color: '#00b42a' },
  { key: 'storagePercent' as MetricKey, title: '存储使用率', color: '#ff7d00' },
  { key: 'networkPercent' as MetricKey, title: '网络占用率', color: '#722ed1' }
];

const loading = ref(true);
const hasLoaded = ref(false);
const data = ref<any>({ current: {}, history: [], overview: {} });
const hover = ref<{ point: ChartPoint; key: MetricKey } | null>(null);
let timer: number | undefined;

const load = async () => {
  loading.value = true;
  try {
    data.value = (await Request.get<any>('/api/v1/gm/monitor')).data || {};
    hasLoaded.value = true;
  } catch (error: any) {
    Message.error(error?.message || '监控加载失败');
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  load();
  timer = window.setInterval(load, 5000);
});
onBeforeUnmount(() => { if (timer) window.clearInterval(timer); });

const current = computed(() => data.value.current || {});
const history = computed(() => Array.isArray(data.value.history) ? data.value.history : []);
const online = computed(() => data.value.overview?.online || []);
const todayActive = computed(() => data.value.overview?.todayActive || []);

const metricValue = (row: any, key: MetricKey) => Math.min(100, Math.max(0, Number(row?.[key] || 0)));
const averageValue = (key: MetricKey) => {
  const rows = history.value;
  if (!rows.length) return metricValue(current.value, key);
  const total = rows.reduce((sum: number, row: any) => sum + metricValue(row, key), 0);
  return total / rows.length;
};
const series = (key: MetricKey): ChartPoint[] => {
  const rows = history.value;
  if (!rows.length) return [];
  const step = Math.max(1, Math.ceil(rows.length / 160));
  const sampled = rows.filter((_: any, index: number) => index % step === 0 || index === rows.length - 1);
  return sampled.map((row: any, index: number) => {
    const x = sampled.length === 1 ? PLOT_LEFT + PLOT_WIDTH / 2 : PLOT_LEFT + (index / (sampled.length - 1)) * PLOT_WIDTH;
    const value = metricValue(row, key);
    return { x, y: PLOT_TOP + PLOT_HEIGHT - (value / 100) * PLOT_HEIGHT, value, timestamp: Number(row.timestamp || 0), source: row };
  });
};
const linePoints = (key: MetricKey) => series(key).map(point => `${point.x},${point.y}`).join(' ');
const tickY = (tick: number) => PLOT_TOP + PLOT_HEIGHT - (tick / 100) * PLOT_HEIGHT;
const setHover = (key: MetricKey, point: ChartPoint) => { hover.value = { key, point }; };
const clearHover = () => { hover.value = null; };
const tooltipStyle = computed(() => {
  const point = hover.value?.point;
  if (!point) return {};
  const left = Math.min(94, Math.max(6, point.x / CHART_WIDTH * 100));
  const top = Math.max(4, point.y / CHART_HEIGHT * 100 - 18);
  return { left: `${left}%`, top: `${top}%` };
});
const formatTime = (timestamp: any) => {
  const value = Number(timestamp || 0);
  return value ? new Date(value).toLocaleString() : '-';
};
const bytes = (value: any) => {
  const amount = Number(value || 0);
  if (amount >= 1024 * 1024) return `${(amount / 1024 / 1024).toFixed(2)} MB/s`;
  return `${(amount / 1024).toFixed(1)} KB/s`;
};
const networkDetail = computed(() => `${bytes(current.value.networkRx)} ↓ / ${bytes(current.value.networkTx)} ↑`);
const lastUpdated = computed(() => formatTime(current.value.timestamp));
</script>

<template>
  <div class="monitor-page">
    <div class="monitor-heading">
      <div>
        <h2>服务器监控</h2>
        <span>每 5 秒刷新，曲线保留最近 24 小时</span>
      </div>
      <a-tag color="arcoblue">更新于 {{ lastUpdated }}</a-tag>
    </div>

  <a-spin :loading="loading && !hasLoaded" style="width: 100%">
      <a-space direction="vertical" fill size="large">
        <a-row :gutter="16">
          <a-col :span="6"><a-card class="metric-card"><div class="metric-card-title">CPU 使用率 / 24 小时平均使用率</div><div class="metric-card-value">{{ metricValue(current, 'cpu').toFixed(1) }}% <span>/</span> {{ averageValue('cpu').toFixed(1) }}%</div></a-card></a-col>
          <a-col :span="6"><a-card class="metric-card"><div class="metric-card-title">内存使用率 / 24 小时平均使用率</div><div class="metric-card-value">{{ metricValue(current, 'memoryPercent').toFixed(1) }}% <span>/</span> {{ averageValue('memoryPercent').toFixed(1) }}%</div></a-card></a-col>
          <a-col :span="6"><a-card class="metric-card"><div class="metric-card-title">存储使用率 / 24 小时平均使用率</div><div class="metric-card-value">{{ metricValue(current, 'storagePercent').toFixed(1) }}% <span>/</span> {{ averageValue('storagePercent').toFixed(1) }}%</div></a-card></a-col>
          <a-col :span="6"><a-card class="metric-card"><div class="metric-card-title">网络使用率 / 24 小时平均使用率</div><div class="metric-card-value">{{ metricValue(current, 'networkPercent').toFixed(1) }}% <span>/</span> {{ averageValue('networkPercent').toFixed(1) }}%</div><div class="network-detail">{{ networkDetail }}</div></a-card></a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col v-for="chart in charts" :key="chart.key" :span="12">
            <a-card :title="chart.title">
              <div class="chart-shell" @mouseleave="clearHover">
                <svg class="metric-chart" :viewBox="`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`" role="img" :aria-label="chart.title">
                  <g class="grid-lines">
                    <line v-for="tick in axisTicks" :key="tick" :x1="PLOT_LEFT" :x2="CHART_WIDTH - PLOT_RIGHT" :y1="tickY(tick)" :y2="tickY(tick)" />
                    <text v-for="tick in axisTicks" :key="`label-${tick}`" :x="PLOT_LEFT - 8" :y="tickY(tick) + 4" text-anchor="end">{{ tick }}%</text>
                  </g>
                  <line class="axis-line" :x1="PLOT_LEFT" :x2="PLOT_LEFT" :y1="PLOT_TOP" :y2="CHART_HEIGHT - PLOT_BOTTOM" />
                  <line class="axis-line" :x1="PLOT_LEFT" :x2="CHART_WIDTH - PLOT_RIGHT" :y1="CHART_HEIGHT - PLOT_BOTTOM" :y2="CHART_HEIGHT - PLOT_BOTTOM" />
                  <polyline v-if="series(chart.key).length" class="metric-line" :stroke="chart.color" :points="linePoints(chart.key)" />
                  <g v-for="point in series(chart.key)" :key="`${chart.key}-${point.timestamp}-${point.x}`">
                    <circle class="metric-point" :cx="point.x" :cy="point.y" r="3.5" :fill="chart.color" @mouseenter="setHover(chart.key, point)" />
                  </g>
                  <text class="axis-label" :x="PLOT_LEFT" :y="CHART_HEIGHT - 8">24 小时前</text>
                  <text class="axis-label" :x="CHART_WIDTH - PLOT_RIGHT" :y="CHART_HEIGHT - 8" text-anchor="end">现在</text>
                </svg>
                <div v-if="hover && hover.key === chart.key" class="chart-tooltip" :style="tooltipStyle">
                  <strong>{{ charts.find(item => item.key === hover?.key)?.title }}</strong>
                  <span>{{ formatTime(hover.point.timestamp) }}</span>
                  <b>{{ hover.point.value.toFixed(1) }}%</b>
                  <span v-if="hover.key === 'networkPercent'">{{ bytes(hover.point.source.networkRx) }} ↓ / {{ bytes(hover.point.source.networkTx) }} ↑</span>
                </div>
                <a-empty v-if="!series(chart.key).length" description="暂无监控数据" />
              </div>
            </a-card>
          </a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col :span="12"><a-card title="当前在线"><div v-if="online.length" class="player-grid"><a-tag v-for="row in online" :key="`online-${row.id}`" color="arcoblue">{{ row.name }} <span>#{{ row.id }}</span></a-tag></div><a-empty v-else description="暂无在线角色" /></a-card></a-col>
          <a-col :span="12"><a-card title="今日活跃"><div v-if="todayActive.length" class="player-grid"><a-tag v-for="row in todayActive" :key="`active-${row.id}`" color="green">{{ row.name }} <span>#{{ row.id }}</span></a-tag></div><a-empty v-else description="暂无活跃角色" /></a-card></a-col>
        </a-row>

        <a-card title="运营概览"><a-descriptions :column="3" bordered><a-descriptions-item label="账号总数">{{ data.overview?.totalAccounts || 0 }}</a-descriptions-item><a-descriptions-item label="今日注册角色">{{ data.overview?.todayRegistrations || 0 }}</a-descriptions-item><a-descriptions-item label="当前在线角色">{{ online.length }}</a-descriptions-item></a-descriptions></a-card>
      </a-space>
    </a-spin>
  </div>
</template>

<style scoped lang="less">
.monitor-page { padding: 16px; }
.monitor-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.monitor-heading h2 { margin: 0 0 4px; color: var(--color-text-1); font-size: 20px; }
.monitor-heading span { color: var(--color-text-3); font-size: 13px; }
.metric-card { min-height: 116px; }
.metric-card-title { color: var(--color-text-2); font-size: 13px; line-height: 1.5; }
.metric-card-value { margin-top: 10px; color: var(--color-text-1); font-size: 27px; line-height: 1.1; white-space: nowrap; }
.metric-card-value span { margin: 0 4px; color: var(--color-text-3); font-size: 20px; }
.network-detail { margin-top: 6px; color: var(--color-text-3); font-size: 12px; }
.chart-shell { position: relative; min-height: 240px; overflow: hidden; }
.metric-chart { display: block; width: 100%; height: 240px; }
.grid-lines line { stroke: var(--color-border-2); stroke-width: 1; stroke-dasharray: 3 4; }
.grid-lines text, .axis-label { fill: var(--color-text-3); font-size: 11px; }
.axis-line { stroke: var(--color-border-3); stroke-width: 1; }
.metric-line { fill: none; stroke-width: 2.5; stroke-linejoin: round; stroke-linecap: round; }
.metric-point { stroke: #fff; stroke-width: 1.5; cursor: crosshair; }
.metric-point:hover { r: 5; }
.chart-tooltip { position: absolute; z-index: 2; display: flex; flex-direction: column; gap: 2px; min-width: 148px; padding: 8px 10px; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-bg-2); box-shadow: 0 6px 18px rgba(0, 0, 0, .16); color: var(--color-text-2); font-size: 12px; pointer-events: none; transform: translate(-50%, -100%); }
.chart-tooltip strong { color: var(--color-text-1); }
.chart-tooltip b { color: var(--color-text-1); font-size: 16px; }
.player-grid { display: flex; flex-wrap: wrap; gap: 8px; max-height: 240px; overflow: auto; align-content: flex-start; }
.player-grid :deep(.arco-tag) { max-width: 100%; }
.player-grid span { opacity: .65; font-size: 11px; }
@media (max-width: 900px) { .monitor-page { padding: 8px; } .monitor-heading { align-items: flex-start; gap: 8px; flex-direction: column; } }
</style>
