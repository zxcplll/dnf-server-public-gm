<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { Message } from '@arco-design/web-vue';
import { gmOperations, type AnalyticsFilters, type AnalyticsMetricOption } from '../../../api/gmOperations';
import MetricTrendChart from '../../../components/admin/operations/MetricTrendChart.vue';

const isoDate = (date: Date) => {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
};
const today = new Date();
const start = new Date(today);
start.setDate(start.getDate() - 29);
const filters = reactive({ startDate: isoDate(start), endDate: isoDate(today), metric: 'new_characters', groupBy: 'none' });
const loading = ref(true);
const hasLoaded = ref(false);
const report = ref<Record<string, any>>({});
const sourceStates = ref<Array<Record<string, any>>>([]);
const updatedAt = ref('');
const exportLoading = ref(false);
let requestSequence = 0;
const numberFormatter = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 });

const metricOptions = ref<AnalyticsMetricOption[]>([
  { value: 'new_characters', label: '新建角色', supported: true, groups: ['job'] },
  { value: 'active_accounts', label: '日活账号', supported: true, groups: ['server'] },
  { value: 'play_time', label: '在线时长', supported: true, groups: ['server'] },
  { value: 'used_fatigue', label: '消耗疲劳', supported: true, groups: [] },
  { value: 'reward_count', label: '奖励发放', supported: true, groups: ['status'] },
  { value: 'currency_changes', label: '货币变更', supported: true, groups: ['status'] },
  { value: 'guild_activity', label: '公会新增成员', supported: true, groups: ['guild'] },
]);
const groupLabels: Record<string, string> = { none: '不分组', job: '按职业', server: '按服务器', status: '按状态', guild: '按公会' };
const selectedMetric = computed(() => metricOptions.value.find((item) => item.value === filters.metric));
const groupOptions = computed(() => ['none', ...(selectedMetric.value?.groups || [])].map((value) => ({ value, label: groupLabels[value] || value })));
const apiFilters = computed<AnalyticsFilters>(() => ({ startDate: filters.startDate, endDate: filters.endDate, metric: filters.metric, groupBy: filters.groupBy }));
const rangeDays = computed(() => Math.floor((new Date(`${filters.endDate}T00:00:00`).getTime() - new Date(`${filters.startDate}T00:00:00`).getTime()) / 86400000) + 1);
const summaryCards = computed(() => {
  const summary = report.value?.summary || {};
  const unit = report.value?.unit || selectedMetric.value?.unit || '';
  return [
    { key: 'total', label: '总计', value: summary.total ?? 0, note: unit, color: '#4de4d2' },
    { key: 'average', label: '日均', value: summary.average ?? 0, note: `${unit}/天`, color: '#6d9dff' },
    { key: 'peak', label: '单日峰值', value: summary.peak ?? 0, note: unit, color: '#ffbc68' },
    { key: 'samples', label: '有效采样', value: summary.samples ?? 0, note: '个时间点', color: '#c58cff' },
  ];
});
const sourceUnavailable = computed(() => report.value?.sourceStatus?.status === 'UNAVAILABLE');
const chartSeries = computed(() => {
  const list = Array.isArray(report.value?.trend) ? report.value.trend : [];
  return [{
    key: filters.metric,
    label: report.value?.label || selectedMetric.value?.label || '趋势',
    color: '#4de4d2',
    points: list.map((point: any) => ({ label: point.date || point.label || '', value: Number(point.value || 0) })),
  }];
});
const breakdownRows = computed(() => Array.isArray(report.value?.breakdown) ? report.value.breakdown : []);

const validate = () => {
  if (!filters.startDate || !filters.endDate || rangeDays.value < 1) { Message.error('请选择有效的起止日期'); return false; }
  if (rangeDays.value > 366) { Message.error('统计日期范围最多 366 天'); return false; }
  return true;
};

const load = async () => {
  if (!validate()) return;
  const sequence = ++requestSequence;
  if (!hasLoaded.value) loading.value = true;
  try {
    const payload = await gmOperations.getAnalyticsReport(apiFilters.value);
    if (sequence !== requestSequence) return;
    report.value = payload || {};
    const source = payload?.sourceStatus || {};
    sourceStates.value = [{
      name: payload?.label || selectedMetric.value?.label || '统计数据源',
      available: source.status === 'AVAILABLE',
      message: source.reason || '',
    }];
    updatedAt.value = new Date().toLocaleString();
    hasLoaded.value = true;
  } catch (error: any) {
    if (sequence !== requestSequence) return;
    sourceStates.value = [{ name: '统计数据源', available: false, message: error?.response?.data?.message || error?.message || '读取失败' }];
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
};

const loadMetricOptions = async () => {
  try {
    const options = await gmOperations.getAnalyticsMetrics();
    if (Array.isArray(options) && options.length) metricOptions.value = options;
  } catch {
    // The built-in whitelist keeps the report usable while the metadata endpoint recovers.
  }
};

const exportCsv = async () => {
  if (!validate() || exportLoading.value) return;
  exportLoading.value = true;
  try {
    const payload = await gmOperations.downloadAnalyticsCsv(apiFilters.value);
    const blob = payload instanceof Blob ? payload : new Blob([payload as any], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `运营统计-${filters.startDate}-${filters.endDate}.csv`;
    anchor.click();
    URL.revokeObjectURL(url);
    Message.success('CSV 已导出');
  } catch (error: any) {
    Message.error(error?.response?.data?.message || error?.message || 'CSV 导出失败');
  } finally {
    exportLoading.value = false;
  }
};

watch(() => filters.metric, () => {
  const allowed = selectedMetric.value?.groups || [];
  if (filters.groupBy !== 'none' && !allowed.includes(filters.groupBy)) filters.groupBy = 'none';
});

onMounted(async () => {
  await loadMetricOptions();
  await load();
});
</script>

<template>
  <div class="analytics-page">
    <header class="page-heading">
      <div><h2>运营统计</h2><p>注册、活跃、货币、奖励和公会指标统一分析。</p></div>
      <div class="heading-actions"><span>最近更新 {{ updatedAt || '--' }}</span><a-button :loading="exportLoading" @click="exportCsv"><template #icon><icon-download /></template>导出 CSV</a-button></div>
    </header>

    <a-card class="filter-card">
      <a-form class="gm-filter-form" layout="inline" :model="filters">
        <a-form-item label="开始日期"><a-date-picker v-model="filters.startDate" value-format="YYYY-MM-DD" /></a-form-item>
        <a-form-item label="结束日期"><a-date-picker v-model="filters.endDate" value-format="YYYY-MM-DD" /></a-form-item>
        <a-form-item label="指标"><a-select v-model="filters.metric" style="width: 210px"><a-option v-for="option in metricOptions" :key="option.value" :value="option.value">{{ option.label }}{{ option.supported === false ? '（数据源不可用）' : '' }}</a-option></a-select></a-form-item>
        <a-form-item label="分组"><a-select v-model="filters.groupBy" style="width: 140px"><a-option v-for="option in groupOptions" :key="option.value" :value="option.value">{{ option.label }}</a-option></a-select></a-form-item>
        <a-form-item class="gm-filter-actions"><a-button type="primary" :loading="loading" @click="load"><template #icon><icon-search /></template>生成报表</a-button></a-form-item>
      </a-form>
    </a-card>

    <section class="source-strip">
      <span>数据源</span><a-tag v-for="source in sourceStates" :key="source.name" :color="source.available === false ? 'red' : 'green'">{{ source.name }} · {{ source.available === false ? source.message || '不可用' : '可用' }}</a-tag>
    </section>

    <a-spin :loading="loading && !hasLoaded" class="analytics-spin">
      <section class="summary-grid">
        <div v-for="(card, index) in summaryCards" :key="card.key" class="summary-metric" :style="{ '--metric-accent': card.color || ['#4de4d2', '#6d9dff', '#ffbc68', '#c58cff'][index % 4] }">
          <span>{{ card.label }}</span><strong>{{ sourceUnavailable ? '--' : numberFormatter.format(Number(card.value || 0)) }}</strong><small>{{ card.note || `${rangeDays} 天汇总` }}</small>
        </div>
        <a-empty v-if="!summaryCards.length" description="当前筛选范围暂无汇总数据" />
      </section>

      <section class="report-section">
        <div class="section-heading"><div><h3>趋势</h3><span>{{ filters.startDate }} 至 {{ filters.endDate }}</span></div></div>
        <MetricTrendChart :series="chartSeries" />
      </section>

      <section class="report-section">
        <div class="section-heading"><div><h3>分类明细</h3><span>后端白名单分组：{{ groupLabels[filters.groupBy] || filters.groupBy }}</span></div></div>
        <a-table :data="breakdownRows" :pagination="false" :scroll="{ x: 760 }">
          <template #empty><a-empty description="当前数据源没有可展示的分类记录" /></template>
          <template #columns>
            <a-table-column title="日期 / 分类" :width="220"><template #cell="{ record }"><strong>{{ record.label || record.category || record.date || '--' }}</strong><small class="block-muted">{{ record.key || record.metric || '' }}</small></template></a-table-column>
            <a-table-column title="数值" :width="160"><template #cell="{ record }"><span class="numeric">{{ numberFormatter.format(Number(record.value ?? record.count ?? record.total ?? 0)) }}</span></template></a-table-column>
            <a-table-column title="占比" :width="130"><template #cell="{ record }">{{ record.ratio !== undefined ? `${Number(record.ratio).toFixed(2)}%` : '--' }}</template></a-table-column>
            <a-table-column title="说明" :width="260"><template #cell="{ record }">{{ record.description || record.note || '--' }}</template></a-table-column>
          </template>
        </a-table>
      </section>
    </a-spin>
  </div>
</template>

<style scoped lang="less">
.analytics-page { min-height: 100%; padding: 16px; background: var(--gm-bg); }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 12px; padding: 16px 18px; border: 1px solid var(--gm-rule); border-left: 3px solid var(--gm-blue); border-radius: 8px; background: #0c1726; }
.page-heading h2 { margin: 0; color: var(--gm-text); font-size: 20px; }
.page-heading p, .heading-actions span { margin: 5px 0 0; color: var(--gm-muted); font-size: 12px; }
.heading-actions { display: flex; align-items: center; gap: 12px; }
.source-strip { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; margin: 12px 0; color: var(--gm-muted); font-size: 11px; }
.analytics-spin { display: block; width: 100%; min-height: 360px; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; min-height: 110px; }
.summary-metric { position: relative; min-width: 0; min-height: 108px; padding: 14px 15px; overflow: hidden; border: 1px solid var(--gm-rule); border-radius: 8px; background: var(--gm-surface); }
.summary-metric::after { content: ''; position: absolute; left: 15px; right: 15px; bottom: 0; height: 2px; background: var(--metric-accent); }
.summary-metric span, .summary-metric strong, .summary-metric small { display: block; }
.summary-metric span { color: var(--gm-muted); font-size: 11px; }
.summary-metric strong { margin: 12px 0 7px; overflow: hidden; color: var(--gm-text); font: 750 24px/1 ui-monospace, Consolas, monospace; text-overflow: ellipsis; white-space: nowrap; }
.summary-metric small { color: var(--gm-subtle); }
.summary-metric small.up { color: var(--gm-cyan); }
.summary-metric small.down { color: var(--gm-danger); }
.report-section { margin-top: 12px; padding: 14px; border: 1px solid var(--gm-rule); border-radius: 8px; background: var(--gm-surface); }
.section-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.section-heading h3, .section-heading span { margin: 0; }
.section-heading h3 { color: var(--gm-text); font-size: 15px; }
.section-heading span, .block-muted { display: block; margin-top: 3px; color: var(--gm-muted); font-size: 10px; }
.numeric { color: var(--gm-cyan); font-variant-numeric: tabular-nums; font-weight: 700; }

@media (max-width: 1050px) { .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 860px) {
  .analytics-page { padding: 8px; }
  .page-heading { align-items: flex-start; flex-direction: column; }
  .heading-actions { align-items: stretch; flex-direction: column; width: 100%; }
  .heading-actions .arco-btn { width: 100%; }
  .filter-card :deep(.arco-select-view) { width: 100%; }
}
@media (max-width: 460px) { .summary-grid { grid-template-columns: minmax(0, 1fr); } }
</style>
