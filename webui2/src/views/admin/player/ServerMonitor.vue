<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import Request from '../../../api/Request';
import { gmOperations, type RuntimeHealth } from '../../../api/gmOperations';

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
const health = ref<RuntimeHealth>({ services: [], ports: [], logs: [], warnings: [] });
const healthError = ref('');
const hover = ref<{ point: ChartPoint; key: MetricKey } | null>(null);
let timer: number | undefined;
let requestSequence = 0;
const healthEndpoint = '/api/v1/gm/runtime/health';

const load = async () => {
  const sequence = ++requestSequence;
  if (!hasLoaded.value) loading.value = true;
  const [monitorResult, healthResult] = await Promise.allSettled([
    Request.get<any>('/api/v1/gm/monitor'),
    gmOperations.getRuntimeHealth(),
  ]);
  if (sequence !== requestSequence) return;
  if (monitorResult.status === 'fulfilled') data.value = monitorResult.value.data || {};
  if (healthResult.status === 'fulfilled') {
    health.value = healthResult.value || { services: [], ports: [], logs: [] };
    healthError.value = '';
  } else {
    healthError.value = healthResult.reason?.response?.data?.message || healthResult.reason?.message || '运行时健康服务暂不可用';
  }
  hasLoaded.value = monitorResult.status === 'fulfilled' || healthResult.status === 'fulfilled' || hasLoaded.value;
  loading.value = false;
};

onMounted(() => {
  load();
  timer = window.setInterval(load, 5000);
});
onBeforeUnmount(() => { if (timer) window.clearInterval(timer); requestSequence += 1; });

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
const capacity = (value: any) => {
  if (value === undefined || value === null || value === '') return '--';
  const amount = Math.max(0, Number(value || 0));
  const gibibyte = 1024 ** 3;
  const tebibyte = 1024 ** 4;
  const mebibyte = 1024 ** 2;
  if (amount >= tebibyte) return `${(amount / tebibyte).toFixed(1)} TB`;
  if (amount >= gibibyte) return `${(amount / gibibyte).toFixed(1)} GB`;
  if (amount >= mebibyte) return `${(amount / mebibyte).toFixed(1)} MB`;
  return `${(amount / 1024).toFixed(1)} KB`;
};
const memoryDetail = computed(() => `${capacity(current.value.memoryUsed)} / ${capacity(current.value.memoryTotal)}`);
const storageDetail = computed(() => `${capacity(current.value.storageUsed)} / ${capacity(current.value.storageTotal)}`);
const networkDetail = computed(() => `${bytes(current.value.networkRx)} ↓ / ${bytes(current.value.networkTx)} ↑`);
const lastUpdated = computed(() => formatTime(current.value.timestamp));
const statusColor = (value: any) => {
  const status = String(value || '').toLowerCase();
  if (['healthy', 'online', 'running', 'listening', 'ok', 'active', 'fresh'].includes(status)) return 'green';
  if (['degraded', 'warning', 'slow', 'stale'].includes(status)) return 'orange';
  return 'red';
};
const statusText = (value: any) => {
  const status = String(value || 'unknown').toLowerCase();
  const labels: Record<string, string> = { healthy: '健康', online: '在线', running: '运行中', listening: '监听中', ok: '正常', active: '活跃', fresh: '新鲜', degraded: '降级', warning: '警告', slow: '缓慢', stale: '过期', offline: '离线', stopped: '已停止', closed: '未监听', unknown: '未知' };
  return labels[status] || String(value || '未知');
};
const game = computed(() => health.value.gameProcess || health.value.game || {});
const bridge = computed(() => health.value.bridge || {});
const services = computed(() => Array.isArray(health.value.services) ? health.value.services : []);
const ports = computed(() => Array.isArray(health.value.ports) ? health.value.ports : []);
const logs = computed(() => Array.isArray(health.value.logs) ? health.value.logs : []);
const portAddress = (record: Record<string, any>) => {
  const port = record.port ?? record.name;
  return port === undefined || port === null || port === '' ? '--' : `${record.host || '127.0.0.1'}:${port}`;
};
const formatDuration = (seconds: any) => {
  const amount = Number(seconds || 0);
  if (!amount) return '--';
  const days = Math.floor(amount / 86400);
  const hours = Math.floor((amount % 86400) / 3600);
  const minutes = Math.floor((amount % 3600) / 60);
  return `${days ? `${days}天 ` : ''}${hours}小时 ${minutes}分`;
};
</script>

<template>
  <div class="monitor-page" :data-health-endpoint="healthEndpoint">
    <div class="monitor-heading">
      <div>
        <h2>服务器监控</h2>
        <span>每 5 秒刷新，曲线保留最近 24 小时</span>
      </div>
      <a-tag color="arcoblue">更新于 {{ lastUpdated }}</a-tag>
    </div>

  <a-spin :loading="loading && !hasLoaded" style="width: 100%">
      <a-space direction="vertical" fill size="large">
        <a-alert v-if="healthError" type="warning" show-icon>{{ healthError }}，主机指标仍会继续刷新。</a-alert>
        <a-row :gutter="16">
          <a-col :xs="24" :sm="12" :lg="6"><a-card class="metric-card"><div class="metric-card-title">CPU 使用率 / 24 小时平均使用率</div><div class="metric-card-value">{{ metricValue(current, 'cpu').toFixed(1) }}% <span>/</span> {{ averageValue('cpu').toFixed(1) }}%</div></a-card></a-col>
          <a-col :xs="24" :sm="12" :lg="6"><a-card class="metric-card"><div class="metric-card-title">内存使用率 / 24 小时平均使用率</div><div class="metric-card-value">{{ metricValue(current, 'memoryPercent').toFixed(1) }}% <span>/</span> {{ averageValue('memoryPercent').toFixed(1) }}%</div><div class="capacity-detail">{{ memoryDetail }}</div></a-card></a-col>
          <a-col :xs="24" :sm="12" :lg="6"><a-card class="metric-card"><div class="metric-card-title">存储使用率 / 24 小时平均使用率</div><div class="metric-card-value">{{ metricValue(current, 'storagePercent').toFixed(1) }}% <span>/</span> {{ averageValue('storagePercent').toFixed(1) }}%</div><div class="capacity-detail">{{ storageDetail }}</div></a-card></a-col>
          <a-col :xs="24" :sm="12" :lg="6"><a-card class="metric-card"><div class="metric-card-title">网络使用率 / 24 小时平均使用率</div><div class="metric-card-value">{{ metricValue(current, 'networkPercent').toFixed(1) }}% <span>/</span> {{ averageValue('networkPercent').toFixed(1) }}%</div><div class="network-detail">{{ networkDetail }}</div></a-card></a-col>
        </a-row>

        <a-row :gutter="16">
          <a-col v-for="chart in charts" :key="chart.key" :xs="24" :lg="12">
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
          <a-col :xs="24" :lg="12"><a-card title="当前在线"><div v-if="online.length" class="player-grid"><a-tag v-for="row in online" :key="`online-${row.id}`" color="arcoblue">{{ row.name }} <span>#{{ row.id }}</span></a-tag></div><a-empty v-else description="暂无在线角色" /></a-card></a-col>
          <a-col :xs="24" :lg="12"><a-card title="今日活跃"><div v-if="todayActive.length" class="player-grid"><a-tag v-for="row in todayActive" :key="`active-${row.id}`" color="green">{{ row.name }} <span>#{{ row.id }}</span></a-tag></div><a-empty v-else description="暂无活跃角色" /></a-card></a-col>
        </a-row>

        <a-card title="运营概览"><a-descriptions :column="3" bordered><a-descriptions-item label="账号总数">{{ data.overview?.totalAccounts || 0 }}</a-descriptions-item><a-descriptions-item label="今日注册角色">{{ data.overview?.todayRegistrations || 0 }}</a-descriptions-item><a-descriptions-item label="当前在线角色">{{ online.length }}</a-descriptions-item></a-descriptions></a-card>

        <section class="runtime-section">
          <div class="section-heading"><div><h3>游戏运行时</h3><span>进程、Frida 桥接和主线程探活</span></div><a-tag :color="statusColor(health.status)">{{ statusText(health.status) }}</a-tag></div>
          <div class="runtime-grid">
            <div class="runtime-panel">
              <div class="panel-title"><span>游戏进程</span><a-tag :color="statusColor(game.status)">{{ statusText(game.status) }}</a-tag></div>
              <div class="runtime-kv"><div><span>PID</span><strong>{{ game.pid || '--' }}</strong></div><div><span>运行时间</span><strong>{{ formatDuration(game.uptimeSeconds ?? game.uptime) }}</strong></div><div><span>实际内存</span><strong>{{ capacity(game.rssBytes ?? game.memoryBytes ?? game.memory) }}</strong></div><div><span>线程</span><strong>{{ game.threadCount ?? game.threads ?? '--' }}</strong></div><div><span>可执行</span><strong>{{ game.executable || game.path || '--' }}</strong></div><div><span>更新时间</span><strong>{{ formatTime(game.observedAt ?? health.observedAt) }}</strong></div></div>
            </div>
            <div class="runtime-panel">
              <div class="panel-title"><span>Frida 桥接</span><a-tag :color="statusColor(bridge.status)">{{ statusText(bridge.status) }}</a-tag></div>
              <div class="runtime-kv"><div><span>端点</span><strong>{{ bridge.endpoint || '--' }}</strong></div><div><span>响应耗时</span><strong>{{ bridge.latencyMs !== undefined ? `${bridge.latencyMs} ms` : '--' }}</strong></div><div><span>主线程</span><strong>{{ bridge.mainThreadReady === false ? '不可用' : bridge.mainThreadReady === true ? '就绪' : '--' }}</strong></div><div><span>协议</span><strong>{{ bridge.protocol || 'ASCII JSON' }}</strong></div><div><span>探测时间</span><strong>{{ formatTime(bridge.observedAt ?? health.observedAt) }}</strong></div><div><span>说明</span><strong>{{ bridge.reason || bridge.message || '--' }}</strong></div></div>
            </div>
          </div>
        </section>

        <section class="runtime-section">
          <div class="section-heading"><div><h3>服务状态</h3><span>后台进程与所需监听端口</span></div></div>
          <div class="service-layout">
            <a-table :data="services" :pagination="false" :scroll="{ x: 620 }">
              <template #empty><a-empty description="暂无服务进程资料" /></template>
              <template #columns><a-table-column title="服务" :width="180"><template #cell="{ record }"><strong>{{ record.label || record.name || '--' }}</strong><small class="block-muted">PID {{ record.pid || '--' }}</small></template></a-table-column><a-table-column title="状态" :width="110"><template #cell="{ record }"><a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag></template></a-table-column><a-table-column title="运行时间" :width="150"><template #cell="{ record }">{{ formatDuration(record.uptimeSeconds ?? record.uptime) }}</template></a-table-column><a-table-column title="实际内存" :width="130"><template #cell="{ record }">{{ capacity(record.rssBytes) }}</template></a-table-column><a-table-column title="说明" :width="180"><template #cell="{ record }">{{ record.reason || record.message || record.path || '--' }}</template></a-table-column></template>
            </a-table>
            <a-table :data="ports" :pagination="false" :scroll="{ x: 480 }">
              <template #empty><a-empty description="暂无端口资料" /></template>
              <template #columns><a-table-column title="用途" :width="160"><template #cell="{ record }">{{ record.label || record.name || '--' }}</template></a-table-column><a-table-column title="监听地址" :width="170"><template #cell="{ record }"><span class="mono">{{ portAddress(record) }}</span></template></a-table-column><a-table-column title="状态" :width="110"><template #cell="{ record }"><a-tag :color="statusColor(record.status ?? (record.listening ? 'listening' : 'closed'))">{{ statusText(record.status ?? (record.listening ? 'listening' : 'closed')) }}</a-tag></template></a-table-column><a-table-column title="说明" :width="180"><template #cell="{ record }">{{ record.reason || '--' }}</template></a-table-column></template>
            </a-table>
          </div>
        </section>

        <section class="runtime-section">
          <div class="section-heading"><div><h3>日志新鲜度</h3><span>Frida、DP2、游戏与网页后台日志</span></div></div>
          <a-table :data="logs" :pagination="false" :scroll="{ x: 860 }">
            <template #empty><a-empty description="暂无日志状态资料" /></template>
            <template #columns><a-table-column title="日志" :width="160"><template #cell="{ record }"><strong>{{ record.label || record.name || '--' }}</strong></template></a-table-column><a-table-column title="状态" :width="100"><template #cell="{ record }"><a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag></template></a-table-column><a-table-column title="最后更新" :width="190"><template #cell="{ record }">{{ formatTime(record.updatedAt ?? record.lastModified) }}</template></a-table-column><a-table-column title="大小" :width="120"><template #cell="{ record }">{{ capacity(record.sizeBytes ?? record.size) }}</template></a-table-column><a-table-column title="路径" :width="320"><template #cell="{ record }"><span class="mono">{{ record.path || '--' }}</span></template></a-table-column></template>
          </a-table>
        </section>
      </a-space>
    </a-spin>
  </div>
</template>

<style scoped lang="less">
.monitor-page {
  min-height: 100%;
  padding: 18px;
  background: var(--gm-bg);
}

.monitor-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 15px 18px;
  border: 1px solid var(--gm-rule);
  border-left: 3px solid var(--gm-cyan);
  border-radius: 8px;
  background: #0c1726;

  h2 {
    margin: 0 0 4px;
    color: var(--gm-text);
    font-size: 20px;
    letter-spacing: 0;
  }

  span {
    color: var(--gm-muted);
    font-size: 12px;
  }

  :deep(.arco-tag) {
    border-color: rgba(77, 228, 210, .34);
    color: var(--gm-cyan);
    background: var(--gm-cyan-soft);
  }
}

.monitor-page :deep(.arco-space-item) {
  min-width: 0;
}

.monitor-page :deep(.arco-row) {
  row-gap: 16px;
}

.metric-card {
  position: relative;
  min-height: 138px;
  overflow: hidden;
  border-color: var(--gm-rule);
  background: var(--gm-surface);
  box-shadow: 0 12px 28px rgba(0, 0, 0, .14);

  &::after {
    content: "";
    position: absolute;
    left: 16px;
    right: 16px;
    bottom: 12px;
    height: 2px;
    background: var(--gm-rule);
  }
}

.metric-card-title {
  min-height: 38px;
  color: var(--gm-muted);
  font-size: 12px;
  line-height: 1.5;
}

.metric-card-value {
  margin-top: 7px;
  color: var(--gm-text);
  font: 700 25px/1.1 ui-monospace, SFMono-Regular, Consolas, monospace;
  white-space: nowrap;

  span {
    margin: 0 3px;
    color: var(--gm-subtle);
    font-size: 17px;
  }
}

.network-detail {
  margin-top: 6px;
  color: var(--gm-cyan);
  font-size: 11px;
}

.capacity-detail {
  margin-top: 7px;
  color: var(--gm-cyan);
  font: 600 12px/1.4 ui-monospace, SFMono-Regular, Consolas, monospace;
}

.chart-shell {
  position: relative;
  min-height: 240px;
  overflow: hidden;
  border-radius: 6px;
  background: rgba(8, 13, 22, .34);
}

.metric-chart {
  display: block;
  width: 100%;
  height: 240px;
}

.grid-lines line { stroke: var(--gm-rule); stroke-width: 1; stroke-dasharray: 3 4; }
.grid-lines text,
.axis-label { fill: var(--gm-subtle); font-size: 11px; }
.axis-line { stroke: var(--gm-rule-strong); stroke-width: 1; }
.metric-line { fill: none; stroke-width: 2.5; stroke-linejoin: round; stroke-linecap: round; }
.metric-point { stroke: var(--gm-surface); stroke-width: 1.5; cursor: crosshair; }
.metric-point:hover { r: 5; }

.chart-tooltip {
  position: absolute;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 2px;
  width: min(188px, calc(100% - 16px));
  min-width: 132px;
  padding: 8px 10px;
  border: 1px solid rgba(77, 228, 210, .38);
  border-radius: 6px;
  background: #0b1422;
  box-shadow: 0 10px 28px rgba(0, 0, 0, .32);
  color: var(--gm-muted);
  font-size: 12px;
  pointer-events: none;
  transform: translate(-50%, -100%);

  strong,
  b { color: var(--gm-text); }

  b { font-size: 16px; }
}

.player-grid {
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 8px;
  max-height: 240px;
  overflow: auto;
}

.player-grid :deep(.arco-tag) {
  max-width: 100%;
  overflow: hidden;
  border-color: var(--gm-rule-strong);
  text-overflow: ellipsis;
}

.player-grid span { opacity: .65; font-size: 11px; }

.runtime-section { min-width: 0; padding: 15px; border: 1px solid var(--gm-rule); border-radius: 8px; background: var(--gm-surface); }
.section-heading, .panel-title { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-heading { margin-bottom: 12px; }
.section-heading h3 { margin: 0; color: var(--gm-text); font-size: 15px; }
.section-heading span { display: block; margin-top: 4px; color: var(--gm-muted); font-size: 11px; }
.runtime-grid, .service-layout { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.runtime-panel { min-width: 0; border: 1px solid var(--gm-rule); border-radius: 6px; overflow: hidden; background: rgba(8, 13, 22, .28); }
.panel-title { padding: 10px 12px; border-bottom: 1px solid var(--gm-rule); color: var(--gm-text); font-weight: 700; }
.runtime-kv { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.runtime-kv > div { min-width: 0; min-height: 62px; padding: 9px 11px; border-right: 1px solid var(--gm-rule); border-bottom: 1px solid var(--gm-rule); }
.runtime-kv > div:nth-child(2n) { border-right: 0; }
.runtime-kv span, .runtime-kv strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.runtime-kv span { color: var(--gm-muted); font-size: 10px; }
.runtime-kv strong { margin-top: 6px; color: var(--gm-text); font-size: 12px; }
.block-muted { display: block; margin-top: 3px; color: var(--gm-muted); font-size: 10px; }
.mono { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; overflow-wrap: anywhere; }

@media (max-width: 900px) {
  .monitor-page { padding: 10px; }
  .monitor-heading { align-items: flex-start; flex-direction: column; gap: 8px; }
  .runtime-grid, .service-layout { grid-template-columns: minmax(0, 1fr); }
}

@media (max-width: 480px) {
  .metric-card-value { font-size: 22px; }
  .chart-tooltip { min-width: 120px; font-size: 11px; }
  .runtime-kv { grid-template-columns: minmax(0, 1fr); }
  .runtime-kv > div { border-right: 0; }
}
</style>
