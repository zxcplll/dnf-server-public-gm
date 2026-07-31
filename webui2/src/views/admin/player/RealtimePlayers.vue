<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { gmOperations, type RuntimePlayer } from '../../../api/gmOperations';
import { openPlayerInspector } from '../../../composables/usePlayerInspector';

const runtimeOperation = 'online_snapshot';
const filters = reactive({ keyword: '', page: 1, pageSize: 20 });
const result = ref({ page: 1, pageSize: 20, totalSize: 0, list: [] as RuntimePlayer[] });
const loading = ref(true);
const hasLoaded = ref(false);
const degraded = ref('');
const updatedAt = ref<Date | null>(null);
let timer: number | undefined;
let requestSequence = 0;

const onlineCount = computed(() => result.value.totalSize || result.value.list.length);
const totalGold = computed(() => result.value.list.reduce((sum, row) => sum + Number(row.gold || 0), 0));
const partyCount = computed(() => result.value.list.filter((row) => row.party).length);
const tradingCount = computed(() => result.value.list.filter((row) => row.trading).length);
const numberFormatter = new Intl.NumberFormat('zh-CN');

const normalize = (payload: any) => {
  const list = Array.isArray(payload) ? payload : payload?.list || payload?.players || payload?.online || [];
  return {
    page: Number(payload?.page || filters.page),
    pageSize: Number(payload?.pageSize || filters.pageSize),
    totalSize: Number(payload?.totalSize ?? payload?.total ?? list.length),
    list: list.map((row: any) => ({
      ...row,
      characNo: Number(row.characNo ?? row.id ?? 0),
      characName: row.characName ?? row.name ?? '未知角色',
      accountName: row.accountName ?? row.accountname ?? row.account,
      uid: Number(row.uid ?? row.accountId ?? row.memberId ?? 0) || undefined,
      level: Number(row.level ?? row.lev ?? 0) || undefined,
      party: Boolean(row.party ?? row.inParty),
      trading: Boolean(row.trading ?? row.inTrade),
      mapName: row.mapName || (row.village !== undefined ? `村庄 ${row.village} · 区域 ${row.area ?? '-'}` : undefined),
      jobName: row.jobName || (row.job !== undefined ? `职业 ${row.job}` : undefined),
      growTypeName: row.growTypeName || (row.growType !== undefined ? `转职 ${row.growType}` : undefined),
      online: row.online !== false,
    })),
  };
};

const load = async (resetPage = false) => {
  if (resetPage) filters.page = 1;
  const sequence = ++requestSequence;
  if (!hasLoaded.value) loading.value = true;
  try {
    const payload = await gmOperations.getOnline(filters);
    if (sequence !== requestSequence) return;
    result.value = normalize(payload);
    filters.page = result.value.page;
    degraded.value = (payload as any)?.warning || (payload as any)?.reason || '';
    updatedAt.value = new Date();
    hasLoaded.value = true;
  } catch (error: any) {
    if (sequence !== requestSequence) return;
    degraded.value = error?.response?.data?.message || error?.message || '实时桥接暂不可用';
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
};

const onPageChange = (page: number) => { filters.page = page; load(); };
const onPageSizeChange = (pageSize: number) => { filters.pageSize = pageSize; load(true); };
const inspect = (row: RuntimePlayer) => openPlayerInspector(row.characNo, 'profile', '实时在线');

onMounted(() => {
  load();
  timer = window.setInterval(() => load(), 5000);
});
onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer);
  requestSequence += 1;
});
</script>

<template>
  <div class="realtime-page" :data-runtime-operation="runtimeOperation">
    <header class="page-heading">
      <div><h2>实时在线玩家</h2><p>以游戏进程快照为准，仅更新列表数据，不刷新页面。</p></div>
      <div class="heading-status"><span class="live-dot"></span><strong>{{ onlineCount }}</strong><span>人在线</span><small>{{ updatedAt ? updatedAt.toLocaleTimeString() : '等待首次快照' }}</small></div>
    </header>

    <a-alert v-if="degraded" type="warning" show-icon class="degraded-alert">{{ degraded }}，当前保留最后一次成功数据。</a-alert>

    <section class="runtime-summary">
      <div><span>实际在线</span><strong>{{ onlineCount }}</strong><small>Frida 运行时</small></div>
      <div><span>本页金币</span><strong>{{ numberFormatter.format(totalGold) }}</strong><small>角色实时余额</small></div>
      <div><span>组队角色</span><strong>{{ partyCount }}</strong><small>当前队伍状态</small></div>
      <div><span>交易中</span><strong>{{ tradingCount }}</strong><small>实时交易标记</small></div>
    </section>

    <a-card class="filter-card">
      <a-form class="gm-filter-form" layout="inline" :model="filters">
        <a-form-item label="玩家搜索">
          <a-input v-model="filters.keyword" allow-clear placeholder="账号、UID、角色名或角色编号" @press-enter="load(true)"><template #prefix><icon-search /></template></a-input>
        </a-form-item>
        <a-form-item class="gm-filter-actions"><a-space><a-button type="primary" @click="load(true)"><template #icon><icon-search /></template>查询</a-button><a-button :loading="loading" @click="load()"><template #icon><icon-refresh /></template>刷新</a-button></a-space></a-form-item>
      </a-form>
    </a-card>

    <a-card class="table-card">
      <a-table :data="result.list" :loading="loading && !hasLoaded" :pagination="false" :scroll="{ x: 1460 }" row-key="characNo">
        <template #empty><a-empty :description="degraded ? '运行时快照暂不可用' : '当前没有在线角色'" /></template>
        <template #columns>
          <a-table-column title="角色" :width="210" fixed="left"><template #cell="{ record }"><button class="player-link" type="button" @click.stop="inspect(record)"><span class="level-badge">LV.{{ record.level || '-' }}</span><span><strong>{{ record.characName }}</strong><small>#{{ record.characNo }}</small></span></button></template></a-table-column>
          <a-table-column title="账号" :width="180"><template #cell="{ record }"><strong>{{ record.accountName || '--' }}</strong><small class="block-muted">UID {{ record.uid || '--' }}</small></template></a-table-column>
          <a-table-column title="职业" :width="170"><template #cell="{ record }">{{ record.growTypeName || record.jobName || '--' }}</template></a-table-column>
          <a-table-column title="当前金币" :width="150"><template #cell="{ record }"><span class="gold-value">{{ numberFormatter.format(record.gold || 0) }}</span></template></a-table-column>
          <a-table-column title="疲劳" :width="100"><template #cell="{ record }">{{ record.fatigue ?? '--' }}</template></a-table-column>
          <a-table-column title="位置" :width="180"><template #cell="{ record }">{{ record.mapName || (record.mapId !== undefined ? `地图 ${record.mapId}` : '--') }}</template></a-table-column>
          <a-table-column title="队伍" :width="100"><template #cell="{ record }"><a-tag :color="record.party ? 'arcoblue' : 'gray'">{{ record.party ? '组队中' : '单人' }}</a-tag></template></a-table-column>
          <a-table-column title="公会" :width="160"><template #cell="{ record }">{{ record.guildName || '无公会' }}</template></a-table-column>
          <a-table-column title="交易" :width="100"><template #cell="{ record }"><a-tag :color="record.trading ? 'orange' : 'green'">{{ record.trading ? '交易中' : '空闲' }}</a-tag></template></a-table-column>
          <a-table-column title="状态" :width="100"><template #cell><span class="online-state"><span class="live-dot"></span>在线</span></template></a-table-column>
          <a-table-column title="操作" :width="84" fixed="right"><template #cell="{ record }"><a-tooltip content="打开玩家档案"><a-button type="primary" shape="circle" @click.stop="inspect(record)"><icon-eye /></a-button></a-tooltip></template></a-table-column>
        </template>
      </a-table>
      <div class="gm-pagination-row"><a-pagination :current="filters.page" :page-size="filters.pageSize" :total="result.totalSize" show-total show-jumper show-page-size :page-size-options="[20, 50, 100]" @change="onPageChange" @page-size-change="onPageSizeChange" /></div>
    </a-card>
  </div>
</template>

<style scoped lang="less">
.realtime-page { min-height: 100%; padding: 16px; background: var(--gm-bg); }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 12px; padding: 16px 18px; border: 1px solid var(--gm-rule); border-left: 3px solid var(--gm-cyan); border-radius: 8px; background: #0c1726; }
.page-heading h2 { margin: 0; color: var(--gm-text); font-size: 20px; }
.page-heading p { margin: 5px 0 0; color: var(--gm-muted); font-size: 12px; }
.heading-status { display: grid; grid-template-columns: auto auto auto; align-items: baseline; gap: 5px; color: var(--gm-muted); }
.heading-status strong { color: var(--gm-cyan); font: 750 24px/1 ui-monospace, Consolas, monospace; }
.heading-status small { grid-column: 1 / -1; text-align: right; color: var(--gm-subtle); }
.live-dot { display: inline-block; width: 7px; height: 7px; border-radius: 50%; background: var(--gm-cyan); box-shadow: 0 0 10px rgba(77, 228, 210, .7); }
.degraded-alert { margin-bottom: 12px; }
.runtime-summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-bottom: 12px; border: 1px solid var(--gm-rule); border-radius: 8px; overflow: hidden; background: var(--gm-surface); }
.runtime-summary > div { min-width: 0; padding: 13px 15px; border-right: 1px solid var(--gm-rule); }
.runtime-summary > div:last-child { border-right: 0; }
.runtime-summary span, .runtime-summary strong, .runtime-summary small { display: block; }
.runtime-summary span { color: var(--gm-muted); font-size: 11px; }
.runtime-summary strong { margin: 6px 0 4px; overflow: hidden; color: var(--gm-text); font: 750 20px/1 ui-monospace, Consolas, monospace; text-overflow: ellipsis; white-space: nowrap; }
.runtime-summary small { color: var(--gm-subtle); font-size: 10px; }
.filter-card { margin-bottom: 12px; }
.filter-card :deep(.arco-input-wrapper) { width: min(420px, 100%); }
.player-link { display: flex; align-items: center; width: 100%; min-width: 0; gap: 9px; padding: 0; border: 0; color: inherit; background: transparent; text-align: left; cursor: pointer; }
.player-link > span:last-child { min-width: 0; }
.player-link strong, .player-link small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.player-link strong { color: var(--gm-text); font-size: 14px; }
.player-link:hover strong { color: var(--gm-cyan); }
.player-link small, .block-muted { margin-top: 3px; color: var(--gm-muted); font-size: 10px; }
.level-badge { display: inline-flex; align-items: center; justify-content: center; flex: 0 0 52px; height: 31px; border: 1px solid rgba(77, 228, 210, .58); border-radius: 6px; color: var(--gm-cyan); background: var(--gm-cyan-soft); font: 700 10px/1 ui-monospace, Consolas, monospace; }
.gold-value { color: var(--gm-amber); font-variant-numeric: tabular-nums; font-weight: 700; }
.online-state { display: inline-flex; align-items: center; gap: 6px; color: var(--gm-cyan); }

@media (max-width: 860px) {
  .realtime-page { padding: 8px; }
  .page-heading { align-items: flex-start; flex-direction: column; }
  .heading-status small { text-align: left; }
  .runtime-summary { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .runtime-summary > div:nth-child(2) { border-right: 0; }
  .runtime-summary > div:nth-child(-n + 2) { border-bottom: 1px solid var(--gm-rule); }
}

@media (max-width: 460px) {
  .runtime-summary { grid-template-columns: minmax(0, 1fr); }
  .runtime-summary > div { border-right: 0; border-bottom: 1px solid var(--gm-rule); }
  .runtime-summary > div:last-child { border-bottom: 0; }
}
</style>
