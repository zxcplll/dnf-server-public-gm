<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import Request from '../../../api/Request';

type Schedule = { type: string; intervalMinutes: number; enabled: boolean; retainCount: number; nextRunAt?: string };

const loading = ref(false);
const entries = ref<any[]>([]);
const schedules = reactive<Record<string, Schedule>>({
  PVF: { type: 'PVF', intervalMinutes: 1440, enabled: false, retainCount: 10 },
  DB: { type: 'DB', intervalMinutes: 1440, enabled: false, retainCount: 10 }
});
const busyType = ref('');

const scheduleList: Schedule[] = [schedules.PVF!, schedules.DB!];
const scheduleDays = (schedule: Schedule) => (Math.max(1, Number(schedule.intervalMinutes)) * Math.max(1, Number(schedule.retainCount)) / 1440).toFixed(1);
const formatBytes = (value: any) => {
  const bytes = Number(value) || 0;
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`;
};

const load = async () => {
  loading.value = true;
  try {
    const response = await Request.get<any>('/api/v1/gm/backups');
    entries.value = response.data?.entries || [];
    for (const row of response.data?.schedules || []) {
      const type = String(row.type || '').toUpperCase();
      if (!schedules[type]) continue;
      schedules[type].intervalMinutes = Number(row.interval_minutes ?? row.intervalMinutes ?? schedules[type].intervalMinutes);
      schedules[type].enabled = Boolean(Number(row.enabled) || row.enabled === true);
      schedules[type].retainCount = Number(row.retain_count ?? row.retainCount ?? schedules[type].retainCount);
      schedules[type].nextRunAt = row.next_run_at ?? row.nextRunAt;
    }
  } catch (error: any) {
    Message.error(error?.message || '备份列表加载失败');
  } finally {
    loading.value = false;
  }
};

const saveSchedule = async (schedule: Schedule) => {
  busyType.value = schedule.type;
  try {
    await Request.post('/api/v1/gm/backups/schedule', {
      type: schedule.type,
      intervalMinutes: Math.max(1, Number(schedule.intervalMinutes || 1)),
      enabled: schedule.enabled,
      retainCount: Math.max(1, Number(schedule.retainCount || 1))
    });
    Message.success(`${schedule.type === 'PVF' ? 'PVF' : '数据库'}定时备份设置已保存`);
    await load();
  } catch (error: any) {
    Message.error(error?.message || '保存定时备份设置失败');
  } finally {
    busyType.value = '';
  }
};

const manualBackup = async (type: string) => {
  busyType.value = `manual-${type}`;
  try {
    await Request.post(`/api/v1/gm/backups/${type}`);
    Message.success(`${type === 'PVF' ? 'PVF' : '数据库'}备份已完成`);
    await load();
  } catch (error: any) {
    Message.error(error?.message || '手工备份失败');
  } finally {
    busyType.value = '';
  }
};

const restore = (entry: any) => {
  Modal.confirm({
    title: '确认恢复备份',
    content: `将恢复 ${entry.type === 'PVF' ? 'PVF' : '数据库'} 备份 ${entry.id}。数据库恢复会覆盖当前数据，是否继续？`,
    onOk: async () => {
      busyType.value = `restore-${entry.id}`;
      try {
        await Request.post(`/api/v1/gm/backups/${entry.id}/restore`);
        Message.success('备份恢复完成');
        await load();
      } catch (error: any) {
        Message.error(error?.message || '恢复备份失败');
      } finally {
        busyType.value = '';
      }
    }
  });
};

onMounted(load);
</script>

<template>
  <div class="backup-page">
    <a-card title="定时备份设置">
      <a-grid :cols="2" :col-gap="16" :row-gap="16" responsive="screen">
        <a-grid-item v-for="schedule in scheduleList" :key="schedule.type">
          <div class="schedule-card">
            <div class="schedule-heading">
              <strong>{{ schedule.type === 'PVF' ? 'PVF 文件' : '数据库' }}</strong>
              <a-switch v-model="schedule.enabled" checked-text="开启" unchecked-text="关闭" />
            </div>
            <a-form layout="vertical" :model="schedule">
              <a-row :gutter="12">
                <a-col :span="12"><a-form-item label="间隔（分钟）"><a-input-number v-model="schedule.intervalMinutes" :min="1" :max="525600" style="width: 100%" /></a-form-item></a-col>
                <a-col :span="12"><a-form-item label="保存条目上限"><a-input-number v-model="schedule.retainCount" :min="1" :max="1000" style="width: 100%" /></a-form-item></a-col>
              </a-row>
              <div class="schedule-summary">最多保存约 {{ scheduleDays(schedule) }} 天的数据{{ schedule.nextRunAt ? ` · 下次执行 ${schedule.nextRunAt}` : '' }}</div>
              <div class="schedule-actions"><a-button :loading="busyType === `manual-${schedule.type}`" @click="manualBackup(schedule.type)">手工备份</a-button><a-button type="primary" :loading="busyType === schedule.type" @click="saveSchedule(schedule)">保存设置</a-button></div>
            </a-form>
          </div>
        </a-grid-item>
      </a-grid>
    </a-card>

    <a-card title="备份条目" class="entries-card">
      <a-table :data="entries" :loading="loading" :pagination="false" row-key="id">
        <template #columns>
          <a-table-column title="ID" data-index="id" :width="80" />
          <a-table-column title="类型" :width="110"><template #cell="{ record }"><a-tag :color="record.type === 'PVF' ? 'arcoblue' : 'green'">{{ record.type === 'PVF' ? 'PVF' : '数据库' }}</a-tag></template></a-table-column>
          <a-table-column title="大小" :width="120"><template #cell="{ record }">{{ formatBytes(record.size_bytes ?? record.sizeBytes) }}</template></a-table-column>
          <a-table-column title="创建时间" data-index="created_at" :width="180" />
          <a-table-column title="状态" :width="100"><template #cell="{ record }"><a-tag :color="record.status === 'SUCCESS' ? 'green' : 'red'">{{ record.status }}</a-tag></template></a-table-column>
          <a-table-column title="文件路径" data-index="path" ellipsis tooltip />
          <a-table-column title="操作" :width="100"><template #cell="{ record }"><a-button size="small" type="primary" :disabled="record.status !== 'SUCCESS'" :loading="busyType === `restore-${record.id}`" @click="restore(record)">恢复</a-button></template></a-table-column>
        </template>
      </a-table>
      <a-empty v-if="!loading && entries.length === 0" description="暂无备份条目" />
    </a-card>
  </div>
</template>

<style scoped lang="less">
.backup-page { padding: 16px; }
.entries-card { margin-top: 12px; }
.schedule-card { padding: 16px; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-fill-1); }
.schedule-heading { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; font-size: 16px; }
.schedule-summary { color: var(--color-text-3); font-size: 12px; }
.schedule-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }
@media (max-width: 900px) { .backup-page { padding: 8px; } }
</style>
