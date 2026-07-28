<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import Request from '../../../api/Request';
import ItemPicker from '../../../components/ItemPicker.vue';

type RewardItem = {
  item: any | null;
  itemId: number;
  quantity: number;
  upgrade: number;
  separateUpgrade: number;
  sealFlag: boolean;
  highestGrade: boolean;
  amplifyOption: number;
  amplifyValue: number;
};

type TaskForm = {
  name: string;
  intervalMinutes: number;
  targetType: string;
  characterIds: number[];
  message: string;
  gold: number;
  ceraPoint: number;
  enabled: boolean;
  items: RewardItem[];
};

const emptyItem = (): RewardItem => ({
  item: null,
  itemId: 0,
  quantity: 1,
  upgrade: 0,
  separateUpgrade: 0,
  sealFlag: false,
  highestGrade: false,
  amplifyOption: 0,
  amplifyValue: 0
});

const emptyForm = (): TaskForm => ({
  name: '',
  intervalMinutes: 60,
  targetType: 'ALL',
  characterIds: [],
  message: '定时福利',
  gold: 0,
  ceraPoint: 0,
  enabled: false,
  items: [emptyItem()]
});

const loading = ref(false);
const saving = ref(false);
const tasks = ref<any[]>([]);
const formVisible = ref(false);
const editingId = ref<number | null>(null);
const form = reactive<TaskForm>(emptyForm());
const characters = ref<any[]>([]);
const characterLoading = ref(false);

const targetLabel = (type: string) => ({ ALL: '全服玩家', ONLINE: '全服在线', CHARACTERS: '指定角色' }[type] || type);
const characterOptions = computed(() => characters.value.map(item => ({
  label: `${item.characName || item.name || item.characNo}（${item.characNo}）`,
  value: Number(item.characNo || item.id)
})));

const isEquipmentItem = (item: any | null) => {
  const type = String(item?.type || '').toLowerCase();
  return type === 'equipment' || type.includes('equipment') || Boolean(item?.equipmentType || item?.equipmentTypeStr);
};

const onItemChange = (item: RewardItem, value: any | null) => {
  item.itemId = Number(value?.id || 0);
  if (!isEquipmentItem(value)) item.highestGrade = false;
};

const resolveItem = async (item: RewardItem) => {
  if (item.item || item.itemId <= 0) return;
  try {
    const response = await Request.get<any>('/api/v1/pvf/search', {
      params: { keyword: item.itemId, page: 1, pageSize: 1 }
    });
    const match = (response.data?.list || []).find((entry: any) => Number(entry.id) === item.itemId);
    if (match) item.item = match;
  } catch (_) {
    // Keep the saved itemId when the PVF lookup is temporarily unavailable.
  }
};

const loadCharacters = async () => {
  characterLoading.value = true;
  try {
    const response = await Request.get<any>('/api/v1/charac?page=1&pageSize=100');
    characters.value = response.data?.list || [];
  } catch (error: any) {
    Request.showError(error, '角色列表加载失败');
  } finally {
    characterLoading.value = false;
  }
};

const load = async () => {
  loading.value = true;
  try {
    const response = await Request.get<any[]>('/api/v1/gm/reward/tasks');
    tasks.value = Array.isArray(response.data) ? response.data : [];
  } catch (error: any) {
    Request.showError(error, '定时任务加载失败');
  } finally {
    loading.value = false;
  }
};

const resetForm = () => {
  Object.assign(form, emptyForm());
  editingId.value = null;
};

const openCreate = () => {
  resetForm();
  formVisible.value = true;
  loadCharacters();
};

const openEdit = (task: any) => {
  resetForm();
  editingId.value = Number(task.id);
  const payload = task.payload || {};
  Object.assign(form, {
    name: task.name || '',
    intervalMinutes: Number(task.interval_minutes ?? task.intervalMinutes ?? 60),
    targetType: task.target_type || task.targetType || 'ALL',
    characterIds: Array.isArray(payload.characterIds) ? payload.characterIds.map(Number) : [],
    message: payload.message || '定时福利',
    gold: Number(payload.gold || 0),
    ceraPoint: Number(payload.ceraPoint || 0),
    enabled: Boolean(Number(task.enabled) || task.enabled === true),
    items: Array.isArray(payload.items) && payload.items.length ? payload.items.map((item: any) => ({
      ...emptyItem(),
      ...item,
      itemId: Number(item.itemId || 0),
      quantity: Number(item.quantity || 1),
      upgrade: Number(item.upgrade || 0),
      separateUpgrade: Number(item.separateUpgrade ?? item.seperateUpgrade ?? 0),
      highestGrade: Boolean(item.highestGrade),
      amplifyOption: Number(item.amplifyOption || 0),
      amplifyValue: Number(item.amplifyValue || 0)
    })) : [emptyItem()]
  });
  formVisible.value = true;
  loadCharacters();
  void Promise.all(form.items.map(resolveItem));
};

const addItem = () => form.items.push(emptyItem());
const removeItem = (index: number) => {
  if (form.items.length > 1) form.items.splice(index, 1);
};

const normalizedPayload = () => ({
  name: form.name.trim() || '定时福利',
  intervalMinutes: Math.max(1, Number(form.intervalMinutes || 1)),
  targetType: form.targetType,
  characterIds: form.targetType === 'CHARACTERS' ? form.characterIds : [],
  message: form.message,
  gold: Math.max(0, Number(form.gold || 0)),
  ceraPoint: Math.max(0, Number(form.ceraPoint || 0)),
  enabled: form.enabled,
  items: form.items.filter(item => item.itemId > 0).map(item => ({
    itemId: Number(item.item?.id || item.itemId),
    quantity: Math.max(1, Number(item.quantity || 1)),
    upgrade: Number(item.upgrade || 0),
    separateUpgrade: Number(item.separateUpgrade || 0),
    sealFlag: Boolean(item.sealFlag),
    // The backend validates the item type; retaining the flag here keeps existing tasks editable
    // while the asynchronous PVF lookup fills the ItemPicker metadata.
    highestGrade: Boolean(item.highestGrade),
    amplifyOption: Number(item.amplifyOption || 0),
    amplifyValue: Number(item.amplifyValue || 0)
  }))
});

const save = async () => {
  const payload = normalizedPayload();
  if (payload.items.length === 0 && payload.gold === 0 && payload.ceraPoint === 0) {
    Message.error('请至少填写一种物品、金币或点券');
    return;
  }
  if (payload.targetType === 'CHARACTERS' && payload.characterIds.length === 0) {
    Message.error('请选择至少一个发放角色');
    return;
  }
  saving.value = true;
  try {
    if (editingId.value) await Request.put(`/api/v1/gm/reward/tasks/${editingId.value}`, payload);
    else await Request.post('/api/v1/gm/reward/tasks', payload);
    Message.success(editingId.value ? '任务已更新' : '任务已创建');
    formVisible.value = false;
    await load();
  } catch (error: any) {
    Request.showError(error, '保存任务失败');
  } finally {
    saving.value = false;
  }
};

const toggle = async (task: any) => {
  try {
    await Request.put(`/api/v1/gm/reward/tasks/${task.id}/toggle`, undefined, { params: { enabled: !Boolean(Number(task.enabled) || task.enabled === true) } });
    Message.success('任务状态已更新');
    await load();
  } catch (error: any) {
    Request.showError(error, '更新任务状态失败');
  }
};

const remove = (task: any) => {
  Modal.confirm({
    title: '删除定时任务',
    content: `确定删除“${task.name || task.id}”吗？`,
    onOk: async () => {
      try {
        await Request.delete(`/api/v1/gm/reward/tasks/${task.id}`);
        Message.success('任务已删除');
        await load();
      } catch (error: any) {
        Request.showError(error, '删除任务失败');
      }
    }
  });
};

onMounted(load);
</script>

<template>
  <div class="reward-page">
    <a-card>
      <template #title>定时任务管理</template>
      <template #extra><a-button type="primary" @click="openCreate">新建任务</a-button></template>
      <a-table :data="tasks" :loading="loading" :pagination="false" :scroll="{ x: 940 }" row-key="id">
        <template #columns>
          <a-table-column title="任务名称" data-index="name" :width="180" />
          <a-table-column title="发放间隔" :width="130">
            <template #cell="{ record }">每 {{ record.interval_minutes ?? record.intervalMinutes }} 分钟</template>
          </a-table-column>
          <a-table-column title="发放范围" :width="130">
            <template #cell="{ record }">{{ targetLabel(record.target_type || record.targetType) }}</template>
          </a-table-column>
          <a-table-column title="下次执行" data-index="next_run_at" :width="180" />
          <a-table-column title="状态" :width="100">
            <template #cell="{ record }">
              <a-tag :color="Boolean(Number(record.enabled) || record.enabled === true) ? 'green' : 'gray'">
                {{ Boolean(Number(record.enabled) || record.enabled === true) ? '运行中' : '已关闭' }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="220">
            <template #cell="{ record }">
              <a-space>
                <a-button size="small" @click="openEdit(record)">编辑</a-button>
                <a-button size="small" type="primary" @click="toggle(record)">{{ Boolean(Number(record.enabled) || record.enabled === true) ? '关闭' : '开启' }}</a-button>
                <a-button size="small" status="danger" @click="remove(record)">删除</a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:visible="formVisible" :title="editingId ? '编辑定时任务' : '新建定时任务'" :width="'min(980px, calc(100vw - 24px))'" :mask-closable="false" :unmount-on-close="true" :ok-loading="saving" @ok="save">
      <a-form class="task-form" :model="form" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="10"><a-form-item label="任务名称"><a-input v-model="form.name" placeholder="例如：每日登录福利" /></a-form-item></a-col>
          <a-col :span="7"><a-form-item label="发放间隔（分钟）"><a-input-number v-model="form.intervalMinutes" :min="1" :max="10080" style="width: 100%" /></a-form-item></a-col>
          <a-col :span="7"><a-form-item label="启用任务"><a-switch v-model="form.enabled" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="发放范围"><a-select v-model="form.targetType" style="width: 100%"><a-option value="ALL">全服玩家</a-option><a-option value="ONLINE">全服在线</a-option><a-option value="CHARACTERS">指定角色</a-option></a-select></a-form-item></a-col>
          <a-col v-if="form.targetType === 'CHARACTERS'" :span="16"><a-form-item label="发放角色"><a-select v-model="form.characterIds" multiple allow-search :loading="characterLoading" :options="characterOptions" placeholder="选择一个或多个角色" style="width: 100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="金币"><a-input-number v-model="form.gold" :min="0" :max="2147483647" style="width: 100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="点券"><a-input-number v-model="form.ceraPoint" :min="0" :max="2147483647" style="width: 100%" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="邮件说明"><a-input v-model="form.message" /></a-form-item></a-col>
        </a-row>

        <div class="items-heading"><strong>发放物品</strong><a-button size="small" @click="addItem">添加物品</a-button></div>
        <div class="items-help">
          <icon-info-circle />
          <span>数量为邮件中的物品数量；强化和锻造填写等级；最高品级仅对装备生效，按 100% 品质发送。</span>
        </div>
        <div v-for="(item, index) in form.items" :key="index" class="item-editor">
          <div class="item-editor-main item-field"><span class="field-label">物品</span><ItemPicker v-model="item.item" width="100%" @change="(value) => onItemChange(item, value)" /></div>
          <div class="item-field"><span class="field-label">数量</span><a-input-number v-model="item.quantity" :min="1" :max="100000" /></div>
          <div class="item-field"><span class="field-label">强化等级</span><a-input-number v-model="item.upgrade" :min="0" :max="31" /></div>
          <div class="item-field"><span class="field-label">锻造等级</span><a-input-number v-model="item.separateUpgrade" :min="0" :max="31" /></div>
          <div class="item-field"><span class="field-label">增幅属性</span><a-select v-model="item.amplifyOption" style="width: 120px"><a-option :value="0">无</a-option><a-option :value="1">体力</a-option><a-option :value="2">精神</a-option><a-option :value="3">力量</a-option><a-option :value="4">智力</a-option></a-select></div>
          <div class="item-field"><span class="field-label">增幅数值</span><a-input-number v-model="item.amplifyValue" :min="0" :max="65535" /></div>
          <div class="item-field quality-field"><span class="field-label">品质</span><a-checkbox v-model="item.highestGrade" :disabled="!isEquipmentItem(item.item)">最高品级</a-checkbox></div>
          <div class="item-field seal-field"><span class="field-label">封装状态</span><a-switch v-model="item.sealFlag" checked-text="封装" unchecked-text="不封装" /></div>
          <a-button status="danger" size="small" :disabled="form.items.length === 1" @click="removeItem(index)">移除</a-button>
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped lang="less">
.reward-page { padding: 16px; }
.items-heading { display: flex; justify-content: space-between; align-items: center; margin: 8px 0 12px; }
.items-help { display: flex; align-items: flex-start; gap: 6px; color: var(--color-text-3); background: var(--color-fill-2); border-radius: 6px; padding: 8px 10px; margin-bottom: 10px; font-size: 12px; line-height: 1.5; }
.item-editor { display: flex; align-items: flex-end; gap: 10px; padding: 12px; margin-bottom: 8px; background: var(--color-fill-2); border: 1px solid var(--color-border-2); border-radius: 8px; flex-wrap: wrap; }
.item-editor-main { flex: 1 1 300px; min-width: 260px; }
.item-field { display: flex; flex-direction: column; gap: 5px; }
.field-label { color: var(--color-text-2); font-size: 12px; line-height: 1; white-space: nowrap; }
.item-editor :deep(.arco-input-number), .item-editor :deep(.arco-select) { width: 112px; }
.quality-field { min-width: 92px; }
.seal-field { min-width: 92px; }
@media (max-width: 900px) { .reward-page { padding: 8px; } }
@media (max-width: 640px) {
  .task-form :deep(.arco-col) { flex: 0 0 100%; max-width: 100%; }
  .item-editor { align-items: stretch; }
  .item-editor-main { flex-basis: 100%; min-width: 100%; }
  .item-editor > .item-field:not(.item-editor-main) { flex: 1 1 calc(50% - 10px); min-width: 120px; }
  .item-editor :deep(.arco-input-number), .item-editor :deep(.arco-select) { width: 100%; }
}
</style>
