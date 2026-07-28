<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import Request from '../../api/Request';
import router from '../../router';

const loading = ref(true);
const hasLoaded = ref(false);
const data = ref<any>({ overview: {} });
let timer: number | undefined;

const load = async () => {
  loading.value = true;
  try {
    data.value = (await Request.get<any>('/api/v1/gm/monitor')).data || {};
    hasLoaded.value = true;
  } catch {
    // Request already reports the network error; keep the last rendered data in place.
  } finally {
    loading.value = false;
  }
};
const goMonitor = () => router.push('/admin/monitor');
const goReward = () => router.push('/admin/reward/global');
onMounted(() => { load(); timer = window.setInterval(load, 10000); });
onBeforeUnmount(() => { if (timer) window.clearInterval(timer); });
</script>

<template>
  <a-spin :loading="loading && !hasLoaded" style="width: 100%">
    <div class="dashboard-view">
      <section class="welcome-panel">
        <div>
          <span class="eyebrow">DNF ADMIN · OPERATIONS</span>
          <h1>运营总览</h1>
          <p>服务器状态、玩家活跃度和常用操作集中在这里。</p>
        </div>
        <div class="welcome-actions"><a-button type="primary" @click="goMonitor"><icon-dashboard />打开服务器监控</a-button><a-button @click="goReward"><icon-gift />全服发奖励</a-button></div>
      </section>

      <a-row :gutter="14" class="stat-row">
        <a-col :xs="24" :sm="12" :lg="6"><div class="stat-card"><div class="stat-icon blue"><icon-user /></div><span>账号总数</span><strong>{{ data.overview?.totalAccounts || 0 }}</strong><small>全部注册账号</small></div></a-col>
        <a-col :xs="24" :sm="12" :lg="6"><div class="stat-card"><div class="stat-icon green"><icon-user-group /></div><span>当前在线</span><strong>{{ (data.overview?.online || []).length }}</strong><small>实时在线角色</small></div></a-col>
        <a-col :xs="24" :sm="12" :lg="6"><div class="stat-card"><div class="stat-icon purple"><icon-calendar /></div><span>今日上线</span><strong>{{ (data.overview?.todayActive || []).length }}</strong><small>今日活跃角色</small></div></a-col>
        <a-col :xs="24" :sm="12" :lg="6"><div class="stat-card"><div class="stat-icon orange"><icon-user-add /></div><span>今日注册角色</span><strong>{{ data.overview?.todayRegistrations || 0 }}</strong><small>今天新建角色</small></div></a-col>
      </a-row>

      <a-row :gutter="14" class="roster-row">
        <a-col :xs="24" :lg="12"><a-card class="roster-card"><template #title><span class="card-title"><span class="state-dot online"></span>当前在线</span><a-badge :count="(data.overview?.online || []).length" :max-count="9999" /></template><div class="roster-list"><div v-for="row in (data.overview?.online || []).slice(0, 10)" :key="row.id" class="roster-item"><span class="avatar level-badge online-avatar">LV.{{ row.level ?? row.lev ?? '-' }}</span><span class="name">{{ row.name || '未知角色' }}</span><span class="meta">角色 {{ row.id }} · UID {{ row.uid }}</span></div><a-empty v-if="!(data.overview?.online || []).length" description="当前没有在线角色" /></div><template #actions><a-button type="text" size="small" @click="goMonitor">查看完整名单 <icon-right /></a-button></template></a-card></a-col>
        <a-col :xs="24" :lg="12"><a-card class="roster-card"><template #title><span class="card-title"><span class="state-dot today"></span>今日上线</span><a-badge :count="(data.overview?.todayActive || []).length" :max-count="9999" /></template><div class="roster-list"><div v-for="row in (data.overview?.todayActive || []).slice(0, 10)" :key="row.id" class="roster-item"><span class="avatar level-badge today-avatar">LV.{{ row.level ?? row.lev ?? '-' }}</span><span class="name">{{ row.name || '未知角色' }}</span><span class="meta">角色 {{ row.id }} · UID {{ row.uid }}</span></div><a-empty v-if="!(data.overview?.todayActive || []).length" description="今日还没有上线角色" /></div><template #actions><a-button type="text" size="small" @click="goMonitor">查看完整名单 <icon-right /></a-button></template></a-card></a-col>
      </a-row>
    </div>
  </a-spin>
</template>

<style scoped lang="less">
.dashboard-view {
  min-height: 100%;
  padding: 18px;
  background-color: var(--gm-bg);
  background-image:
    linear-gradient(rgba(151, 174, 204, .035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(151, 174, 204, .035) 1px, transparent 1px);
  background-size: 30px 30px;
}

.welcome-panel {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 148px;
  margin-bottom: 14px;
  padding: 24px 26px;
  overflow: hidden;
  border: 1px solid rgba(77, 228, 210, .28);
  border-radius: 8px;
  color: var(--gm-text);
  background: #0c1726;
  box-shadow: inset 3px 0 0 var(--gm-cyan), 0 16px 38px rgba(0, 0, 0, .18);

  &::after {
    content: "LIVE OPERATIONS";
    position: absolute;
    right: 24px;
    bottom: 12px;
    color: rgba(77, 228, 210, .13);
    font: 800 30px/1 ui-monospace, SFMono-Regular, Consolas, monospace;
    pointer-events: none;
  }

  h1 {
    margin: 8px 0 5px;
    color: var(--gm-text);
    font-size: 28px;
    line-height: 1.15;
    letter-spacing: 0;
  }

  p {
    margin: 0;
    color: var(--gm-muted);
    font-size: 13px;
  }
}

.eyebrow {
  color: var(--gm-cyan);
  font: 700 11px/1.2 ui-monospace, SFMono-Regular, Consolas, monospace;
  letter-spacing: .12em;
}

.welcome-actions {
  z-index: 1;
  display: flex;
  gap: 9px;
}

.welcome-actions :deep(.arco-btn-secondary) {
  border-color: var(--gm-rule-strong);
  color: var(--gm-text);
  background: var(--gm-surface-raised);
}

.stat-row,
.roster-row {
  margin-bottom: 14px;
}

.stat-row :deep(.arco-col),
.roster-row :deep(.arco-col) {
  margin-bottom: 14px;
}

.stat-card {
  position: relative;
  min-height: 136px;
  padding: 16px;
  overflow: hidden;
  border: 1px solid var(--gm-rule);
  border-radius: 8px;
  background: var(--gm-surface);
  box-shadow: 0 12px 28px rgba(0, 0, 0, .14);
  transition: border-color 160ms ease, transform 160ms ease;

  &:hover {
    border-color: rgba(77, 228, 210, .44);
    transform: translateY(-2px);
  }

  &::after {
    content: "";
    position: absolute;
    right: 14px;
    bottom: 14px;
    width: 30px;
    height: 1px;
    background: var(--gm-rule-strong);
  }

  > span {
    display: block;
    color: var(--gm-muted);
    font-size: 12px;
  }

  strong {
    display: block;
    margin-top: 5px;
    color: var(--gm-text);
    font: 700 29px/1.15 ui-monospace, SFMono-Regular, Consolas, monospace;
  }

  small {
    color: var(--gm-subtle);
    font-size: 11px;
  }
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  margin-bottom: 11px;
  border: 1px solid currentColor;
  border-radius: 6px;
}

.stat-icon.blue { color: var(--gm-blue); background: var(--gm-blue-soft); }
.stat-icon.green { color: var(--gm-cyan); background: var(--gm-cyan-soft); }
.stat-icon.purple { color: #c58cff; background: rgba(197, 140, 255, .12); }
.stat-icon.orange { color: var(--gm-amber); background: var(--gm-amber-soft); }

.roster-card {
  height: 100%;
  border-radius: 8px;
  border-color: var(--gm-rule);
  background: var(--gm-surface);
  box-shadow: 0 12px 28px rgba(0, 0, 0, .14);
}

.roster-card :deep(.arco-card-body) { padding: 7px 16px 6px; }
.roster-card :deep(.arco-card-actions) { padding: 0 10px; border-top-color: var(--gm-rule); text-align: right; }

.card-title {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--gm-text);
}

.state-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.state-dot.online { background: var(--gm-cyan); box-shadow: 0 0 10px rgba(77, 228, 210, .55); }
.state-dot.today { background: var(--gm-amber); box-shadow: 0 0 10px rgba(255, 188, 104, .42); }

.roster-list {
  min-height: 180px;
  max-height: 375px;
  overflow: auto;
}

.roster-item {
  display: flex;
  align-items: center;
  min-height: 49px;
  gap: 9px;
  border-bottom: 1px solid var(--gm-rule);
}

.roster-item:last-child { border-bottom: 0; }

.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 28px;
  width: 28px;
  height: 28px;
  border: 1px solid currentColor;
  border-radius: 6px;
  font-weight: 700;
}

.online-avatar { color: var(--gm-cyan); background: var(--gm-cyan-soft); }
.today-avatar { color: var(--gm-amber); background: var(--gm-amber-soft); }
.level-badge {
  flex-basis: 50px;
  width: 50px;
  padding: 0 4px;
  font: 700 10px/1 ui-monospace, SFMono-Regular, Consolas, monospace;
  white-space: nowrap;
}
.name { min-width: 0; overflow: hidden; color: var(--gm-text); font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.meta { margin-left: auto; color: var(--gm-subtle); font-size: 12px; white-space: nowrap; }

@media (max-width: 768px) {
  .dashboard-view { padding: 10px; background-size: 24px 24px; }
  .welcome-panel { align-items: flex-start; flex-direction: column; gap: 16px; min-height: 0; padding: 20px; }
  .welcome-panel::after { display: none; }
  .welcome-actions { width: 100%; }
  .welcome-actions .arco-btn { flex: 1 1 0; min-width: 0; }
  .meta { max-width: 45%; overflow: hidden; font-size: 11px; text-overflow: ellipsis; }
}

@media (prefers-reduced-motion: reduce) {
  .stat-card { transition: none; }
}
</style>
