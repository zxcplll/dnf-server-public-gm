<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { Message } from '@arco-design/web-vue';
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
  } catch (error: any) {
    Message.error(error?.message || '首页数据加载失败');
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
        <a-col :xs="12" :lg="6"><div class="stat-card"><div class="stat-icon blue"><icon-user /></div><span>账号总数</span><strong>{{ data.overview?.totalAccounts || 0 }}</strong><small>全部注册账号</small></div></a-col>
        <a-col :xs="12" :lg="6"><div class="stat-card"><div class="stat-icon green"><icon-user-group /></div><span>当前在线</span><strong>{{ (data.overview?.online || []).length }}</strong><small>实时在线角色</small></div></a-col>
        <a-col :xs="12" :lg="6"><div class="stat-card"><div class="stat-icon purple"><icon-calendar /></div><span>今日上线</span><strong>{{ (data.overview?.todayActive || []).length }}</strong><small>今日活跃角色</small></div></a-col>
        <a-col :xs="12" :lg="6"><div class="stat-card"><div class="stat-icon orange"><icon-user-add /></div><span>今日注册角色</span><strong>{{ data.overview?.todayRegistrations || 0 }}</strong><small>今天新建角色</small></div></a-col>
      </a-row>

      <a-row :gutter="14" class="roster-row">
        <a-col :xs="24" :lg="12"><a-card class="roster-card"><template #title><span class="card-title"><span class="state-dot online"></span>当前在线</span><a-badge :count="(data.overview?.online || []).length" :max-count="9999" /></template><div class="roster-list"><div v-for="row in (data.overview?.online || []).slice(0, 10)" :key="row.id" class="roster-item"><span class="avatar online-avatar">{{ String(row.name || '?').slice(0, 1) }}</span><span class="name">{{ row.name || '未知角色' }}</span><span class="meta">角色 {{ row.id }} · UID {{ row.uid }}</span></div><a-empty v-if="!(data.overview?.online || []).length" description="当前没有在线角色" /></div><template #actions><a-button type="text" size="small" @click="goMonitor">查看完整名单 <icon-right /></a-button></template></a-card></a-col>
        <a-col :xs="24" :lg="12"><a-card class="roster-card"><template #title><span class="card-title"><span class="state-dot today"></span>今日上线</span><a-badge :count="(data.overview?.todayActive || []).length" :max-count="9999" /></template><div class="roster-list"><div v-for="row in (data.overview?.todayActive || []).slice(0, 10)" :key="row.id" class="roster-item"><span class="avatar today-avatar">{{ String(row.name || '?').slice(0, 1) }}</span><span class="name">{{ row.name || '未知角色' }}</span><span class="meta">角色 {{ row.id }} · UID {{ row.uid }}</span></div><a-empty v-if="!(data.overview?.todayActive || []).length" description="今日还没有上线角色" /></div><template #actions><a-button type="text" size="small" @click="goMonitor">查看完整名单 <icon-right /></a-button></template></a-card></a-col>
      </a-row>
    </div>
  </a-spin>
</template>

<style scoped lang="less">
.dashboard-view { min-height: 100%; padding: 20px; background: #f5f7fb; }
.welcome-panel { display: flex; justify-content: space-between; align-items: center; padding: 24px 28px; border-radius: 12px; color: #fff; background: linear-gradient(115deg, #172554 0%, #1d4ed8 55%, #2563eb 100%); box-shadow: 0 10px 24px rgba(30,64,175,.18); margin-bottom: 16px; }
.eyebrow { font-size: 11px; letter-spacing: 1.2px; opacity: .7; }
.welcome-panel h1 { margin: 8px 0 4px; font-size: 27px; }
.welcome-panel p { margin: 0; color: rgba(255,255,255,.72); font-size: 13px; }
.welcome-actions { display: flex; gap: 10px; }
.welcome-actions :deep(.arco-btn-secondary) { background: rgba(255,255,255,.14); border-color: rgba(255,255,255,.26); color: #fff; }
.stat-row, .roster-row { margin-bottom: 14px; }
.stat-card { position: relative; background: #fff; border: 1px solid var(--color-border-2); border-radius: 10px; min-height: 133px; padding: 16px; box-shadow: 0 3px 12px rgba(31,35,41,.04); }
.stat-card > span { display: block; color: var(--color-text-2); font-size: 13px; }
.stat-card strong { display: block; margin-top: 5px; color: var(--color-text-1); font-size: 28px; line-height: 1.2; }
.stat-card small { color: var(--color-text-3); font-size: 11px; }
.stat-icon { width: 30px; height: 30px; display: flex; align-items: center; justify-content: center; border-radius: 8px; margin-bottom: 11px; }
.stat-icon.blue { color: #2563eb; background: #e8f0ff; } .stat-icon.green { color: #059669; background: #e8fbf2; } .stat-icon.purple { color: #7c3aed; background: #f1eaff; } .stat-icon.orange { color: #d97706; background: #fff3dc; }
.roster-card { border-radius: 10px; border: 1px solid var(--color-border-2); box-shadow: 0 3px 12px rgba(31,35,41,.04); }
.roster-card :deep(.arco-card-body) { padding: 7px 16px 6px; }
.roster-card :deep(.arco-card-actions) { padding: 0 10px; text-align: right; }
.card-title { display: inline-flex; align-items: center; gap: 7px; }
.state-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; } .state-dot.online { background: #10b981; } .state-dot.today { background: #8b5cf6; }
.roster-list { min-height: 180px; max-height: 375px; overflow: auto; }
.roster-item { display: flex; align-items: center; gap: 9px; min-height: 47px; border-bottom: 1px solid var(--color-border-2); }
.roster-item:last-child { border-bottom: 0; }
.avatar { width: 28px; height: 28px; border-radius: 8px; display: inline-flex; align-items: center; justify-content: center; font-weight: 600; }
.online-avatar { color: #059669; background: #e8fbf2; } .today-avatar { color: #7c3aed; background: #f1eaff; }
.name { color: var(--color-text-1); font-weight: 600; } .meta { margin-left: auto; color: var(--color-text-3); font-size: 12px; }
@media (max-width: 768px) { .dashboard-view { padding: 10px; } .welcome-panel { align-items: flex-start; flex-direction: column; gap: 16px; padding: 20px; } .welcome-actions { width: 100%; } .welcome-actions .arco-btn { flex: 1; } .meta { font-size: 11px; } }
</style>
