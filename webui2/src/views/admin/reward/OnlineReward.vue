<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { Message } from '@arco-design/web-vue';
import Request from '../../../api/Request';

type RewardForm = { enabled: boolean; intervalMinutes: number; ceraPoint: number; gold: number };

const form = reactive<RewardForm>({ enabled: false, intervalMinutes: 60, ceraPoint: 0, gold: 0 });
const progress = ref<any[]>([]);
const logs = ref<any[]>([]);
const onlineCount = ref(0);
const lastRunAt = ref<any>(null);
const updatedAt = ref<any>(null);
const initialLoading = ref(true);
const saving = ref(false);
let timer: number | undefined;

const boolValue = (value: any) => value === true || Number(value) === 1 || value === '1';
const dateValue = (row: any, camel: string, snake: string) => row?.[camel] ?? row?.[snake];
const formatDate = (value: any) => {
  if (!value) return '-';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString();
};
const duration = (value: any) => {
  if (!value) return '-';
  const started = new Date(value).getTime();
  if (!Number.isFinite(started)) return '-';
  const minutes = Math.max(0, Math.floor((Date.now() - started) / 60000));
  if (minutes < 60) return `${minutes} 分钟`;
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`;
};
const progressRows = computed(() => progress.value.map(row => ({
  ...row,
  characNo: row.characNo ?? row.charac_no,
  characName: row.characName ?? row.charac_name,
  onlineSince: dateValue(row, 'onlineSince', 'online_since'),
  lastSeenAt: dateValue(row, 'lastSeenAt', 'last_seen_at'),
  lastAwardAt: dateValue(row, 'lastAwardAt', 'last_award_at'),
  awardCount: row.awardCount ?? row.award_count ?? 0
})));
const logRows = computed(() => logs.value.map(row => ({
  ...row,
  characNo: row.characNo ?? row.charac_no,
  characName: row.characName ?? row.charac_name,
  intervalMinutes: row.intervalMinutes ?? row.interval_minutes,
  ceraPoint: row.ceraPoint ?? row.cera_point,
  awardedAt: dateValue(row, 'awardedAt', 'awarded_at')
})));

const load = async (showInitial = false) => {
  if (showInitial) initialLoading.value = true;
  try {
    const [settingResponse, logResponse] = await Promise.all([
      Request.get<any>('/api/v1/gm/online-reward'),
      Request.get<any>('/api/v1/gm/online-reward/logs?page=1&pageSize=50')
    ]);
    const setting = settingResponse.data || {};
    form.enabled = boolValue(setting.enabled);
    form.intervalMinutes = Number(setting.intervalMinutes ?? setting.interval_minutes ?? 60);
    form.ceraPoint = Number(setting.ceraPoint ?? setting.cera_point ?? 0);
    form.gold = Number(setting.gold ?? 0);
    onlineCount.value = Number(setting.onlineCount ?? setting.online_count ?? 0);
    progress.value = Array.isArray(setting.progress) ? setting.progress : [];
    lastRunAt.value = setting.lastRunAt ?? setting.last_run_at;
    updatedAt.value = setting.updatedAt ?? setting.updated_at;
    logs.value = Array.isArray(logResponse.data) ? logResponse.data : (logResponse.data?.list || []);
  } catch (error: any) {
    Message.error(error?.message || '在线泡点配置加载失败');
  } finally {
    initialLoading.value = false;
  }
};

const save = async () => {
  saving.value = true;
  try {
    await Request.put('/api/v1/gm/online-reward', {
      enabled: form.enabled,
      intervalMinutes: Math.max(1, Math.min(10080, Number(form.intervalMinutes || 1))),
      ceraPoint: Math.max(0, Number(form.ceraPoint || 0)),
      gold: Math.max(0, Number(form.gold || 0))
    });
    Message.success('在线泡点设置已保存');
    await load();
  } catch (error: any) {
    Message.error(error?.message || '在线泡点设置保存失败');
  } finally {
    saving.value = false;
  }
};

const toggle = async (enabled: boolean) => {
  form.enabled = enabled;
  await save();
};

onMounted(() => {
  load(true);
  timer = window.setInterval(() => load(), 15000);
});
onBeforeUnmount(() => { if (timer) window.clearInterval(timer); });
</script>

<template>
  <div class="online-reward-page">
    <a-spin :loading="initialLoading" style="width: 100%">
      <div class="page-heading">
        <div>
          <h2>在线泡点</h2>
          <p>玩家连续在线达到设定时长后，自动发放点券和金币。</p>
        </div>
        <a-tag :color="form.enabled ? 'green' : 'gray'">{{ form.enabled ? '运行中' : '已关闭' }}</a-tag>
      </div>

      <a-row :gutter="16" class="summary-row">
        <a-col :xs="24" :sm="8"><div class="summary-card"><span>当前在线角色</span><strong>{{ onlineCount }}</strong><small>每 15 秒同步一次</small></div></a-col>
        <a-col :xs="24" :sm="8"><div class="summary-card"><span>发放间隔</span><strong>{{ form.intervalMinutes }}<em>分钟</em></strong><small>连续在线时长</small></div></a-col>
        <a-col :xs="24" :sm="8"><div class="summary-card"><span>每次奖励</span><strong>{{ form.ceraPoint + form.gold > 0 ? '已设置' : '未设置' }}</strong><small>{{ form.ceraPoint }} 点券 + {{ form.gold }} 金币</small></div></a-col>
      </a-row>

      <a-card title="泡点规则" class="settings-card">
        <template #extra><a-switch v-model="form.enabled" checked-text="开启" unchecked-text="关闭" @change="(value) => toggle(boolValue(value))" /></template>
        <a-alert type="info" show-icon>角色连续在线达到间隔后发放一次；断线超过 45 秒会重新开始计时。点券和金币都填 0 时不会发放。</a-alert>
        <a-form :model="form" layout="vertical" class="settings-form">
          <a-row :gutter="16">
            <a-col :xs="24" :md="8"><a-form-item label="连续在线时长（分钟）"><a-input-number v-model="form.intervalMinutes" :min="1" :max="10080" style="width: 100%" /></a-form-item></a-col>
            <a-col :xs="24" :md="8"><a-form-item label="每次点券"><a-input-number v-model="form.ceraPoint" :min="0" :max="2147483647" style="width: 100%" /></a-form-item></a-col>
            <a-col :xs="24" :md="8"><a-form-item label="每次金币"><a-input-number v-model="form.gold" :min="0" :max="2147483647" style="width: 100%" /></a-form-item></a-col>
          </a-row>
          <div class="settings-footer"><span>上次扫描：{{ formatDate(lastRunAt) }}<i> · </i>配置更新：{{ formatDate(updatedAt) }}</span><a-button type="primary" :loading="saving" @click="save">保存设置</a-button></div>
        </a-form>
      </a-card>

      <a-card title="在线角色计时" class="table-card">
        <a-table :data="progressRows" :pagination="false" :loading="initialLoading" row-key="characNo">
          <template #columns>
            <a-table-column title="角色" :width="180"><template #cell="{ record }">{{ record.characName || '-' }} <span class="muted">#{{ record.characNo }}</span></template></a-table-column>
            <a-table-column title="开始计时"><template #cell="{ record }">{{ formatDate(record.onlineSince) }}</template></a-table-column>
            <a-table-column title="连续时长"><template #cell="{ record }">{{ duration(record.onlineSince) }}</template></a-table-column>
            <a-table-column title="上次发放"><template #cell="{ record }">{{ formatDate(record.lastAwardAt) }}</template></a-table-column>
            <a-table-column title="累计次数" data-index="awardCount" :width="100" />
          </template>
        </a-table>
        <a-empty v-if="!initialLoading && progressRows.length === 0" description="暂无在线角色计时记录" />
      </a-card>

      <a-card title="发放记录" class="table-card">
        <a-table :data="logRows" :pagination="false" row-key="id">
          <template #columns>
            <a-table-column title="角色" :width="180"><template #cell="{ record }">{{ record.characName || '-' }} <span class="muted">#{{ record.characNo }}</span></template></a-table-column>
            <a-table-column title="间隔"><template #cell="{ record }">{{ record.intervalMinutes }} 分钟</template></a-table-column>
            <a-table-column title="点券" data-index="ceraPoint" />
            <a-table-column title="金币" data-index="gold" />
            <a-table-column title="发放时间"><template #cell="{ record }">{{ formatDate(record.awardedAt) }}</template></a-table-column>
            <a-table-column title="状态"><template #cell="{ record }"><a-tag :color="record.status === 'SUCCESS' ? 'green' : 'red'">{{ record.status === 'SUCCESS' ? '成功' : '失败' }}</a-tag></template></a-table-column>
            <a-table-column title="说明" data-index="message" ellipsis tooltip />
          </template>
        </a-table>
        <a-empty v-if="logRows.length === 0" description="暂无发放记录" />
      </a-card>
    </a-spin>
  </div>
</template>

<style scoped lang="less">
.online-reward-page { padding: 16px; background: #f5f7fb; min-height: 100%; }
.page-heading { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.page-heading h2 { margin: 0 0 5px; font-size: 22px; color: var(--color-text-1); }
.page-heading p { margin: 0; color: var(--color-text-3); font-size: 13px; }
.summary-row { margin-bottom: 16px; }
.summary-card { min-height: 112px; padding: 16px; border: 1px solid var(--color-border-2); border-radius: 10px; background: #fff; box-shadow: 0 3px 12px rgba(31,35,41,.04); }
.summary-card span, .summary-card small { display: block; color: var(--color-text-3); font-size: 12px; }
.summary-card strong { display: block; margin: 7px 0 3px; color: var(--color-text-1); font-size: 28px; line-height: 1.1; }
.summary-card em { margin-left: 4px; color: var(--color-text-3); font-size: 12px; font-style: normal; font-weight: 400; }
.settings-card, .table-card { margin-bottom: 16px; border-radius: 10px; }
.settings-form { margin-top: 18px; }
.settings-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: var(--color-text-3); font-size: 12px; }
.settings-footer i { font-style: normal; color: var(--color-border-3); }
.muted { color: var(--color-text-3); font-size: 12px; }
@media (max-width: 700px) { .online-reward-page { padding: 8px; } .settings-footer { align-items: flex-start; flex-direction: column; } .settings-footer .arco-btn { width: 100%; } }
</style>
