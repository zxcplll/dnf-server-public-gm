<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
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

const emptyItem = (): RewardItem => ({ item: null, itemId: 0, quantity: 1, upgrade: 0, separateUpgrade: 0, sealFlag: false, highestGrade: false, amplifyOption: 0, amplifyValue: 0 });
const form = reactive({
  targetType: 'ONLINE',
  characterIds: [] as number[],
  message: '全服奖励',
  gold: 0,
  ceraPoint: 0,
  items: [emptyItem()]
});
const submitting = ref(false);
const characterLoading = ref(false);
const characters = ref<any[]>([]);

const characterOptions = () => characters.value.map(item => ({
  label: `${item.characName || item.name || item.characNo}（${item.characNo}）`,
  value: Number(item.characNo || item.id)
}));

const isEquipmentItem = (item: any | null) => {
  const type = String(item?.type || '').toLowerCase();
  return type === 'equipment' || type.includes('equipment') || Boolean(item?.equipmentType || item?.equipmentTypeStr);
};

const onItemChange = (item: RewardItem, value: any | null) => {
  item.itemId = Number(value?.id || 0);
  if (!isEquipmentItem(value)) item.highestGrade = false;
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

const addItem = () => form.items.push(emptyItem());
const removeItem = (index: number) => {
  if (form.items.length > 1) form.items.splice(index, 1);
};

const reset = () => {
  form.targetType = 'ONLINE';
  form.characterIds = [];
  form.message = '全服奖励';
  form.gold = 0;
  form.ceraPoint = 0;
  form.items.splice(0, form.items.length, emptyItem());
};

const submit = async () => {
  const items = form.items.filter(item => item.itemId > 0).map(item => ({
    itemId: Number(item.item?.id || item.itemId),
    quantity: Math.max(1, Number(item.quantity || 1)),
    upgrade: Number(item.upgrade || 0),
    separateUpgrade: Number(item.separateUpgrade || 0),
    sealFlag: Boolean(item.sealFlag),
    highestGrade: Boolean(item.highestGrade && isEquipmentItem(item.item)),
    amplifyOption: Number(item.amplifyOption || 0),
    amplifyValue: Number(item.amplifyValue || 0)
  }));
  if (items.length === 0 && Number(form.gold) <= 0 && Number(form.ceraPoint) <= 0) {
    Message.error('请至少填写一种物品、金币或点券');
    return;
  }
  if (form.targetType === 'CHARACTERS' && form.characterIds.length === 0) {
    Message.error('请选择至少一个发放角色');
    return;
  }
  Modal.confirm({
    title: '确认全服发放',
    content: form.targetType === 'ONLINE' ? '奖励将发送给当前在线玩家。' : form.targetType === 'ALL' ? '奖励将发送给所有玩家。' : `奖励将发送给 ${form.characterIds.length} 个指定角色。`,
    onOk: async () => {
      submitting.value = true;
      try {
        const response = await Request.post<any>('/api/v1/gm/reward/global', {
          targetType: form.targetType,
          characterIds: form.characterIds,
          message: form.message,
          gold: Math.max(0, Number(form.gold || 0)),
          ceraPoint: Math.max(0, Number(form.ceraPoint || 0)),
          items
        });
        Message.success(`发放完成：${response.data?.recipientCount ?? 0} 个角色，${response.data?.mailCount ?? 0} 封邮件`);
        reset();
      } catch (error: any) {
        Request.showError(error, '全服发放失败');
      } finally {
        submitting.value = false;
      }
    }
  });
};

onMounted(loadCharacters);
</script>

<template>
  <div class="reward-page">
    <a-card title="全服奖励发放">
      <template #extra><a-tag color="orange">操作会立即发送邮件</a-tag></template>
      <a-form :model="form" layout="vertical">
        <a-row :gutter="16">
          <a-col :xs="24" :md="8"><a-form-item label="发放范围"><a-select v-model="form.targetType" style="width: 100%"><a-option value="ONLINE">全服在线玩家</a-option><a-option value="ALL">所有玩家</a-option><a-option value="CHARACTERS">指定角色</a-option></a-select></a-form-item></a-col>
          <a-col v-if="form.targetType === 'CHARACTERS'" :xs="24" :md="16"><a-form-item label="发放角色"><a-select v-model="form.characterIds" multiple allow-search :loading="characterLoading" :options="characterOptions()" placeholder="选择一个或多个角色" style="width: 100%" /></a-form-item></a-col>
          <a-col :xs="24" :md="8"><a-form-item label="金币"><a-input-number v-model="form.gold" :min="0" :max="2147483647" style="width: 100%" /></a-form-item></a-col>
          <a-col :xs="24" :md="8"><a-form-item label="点券"><a-input-number v-model="form.ceraPoint" :min="0" :max="2147483647" style="width: 100%" /></a-form-item></a-col>
          <a-col :xs="24" :md="8"><a-form-item label="邮件说明"><a-input v-model="form.message" /></a-form-item></a-col>
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
        <div class="submit-row"><a-button type="primary" :loading="submitting" @click="submit">立即发放</a-button></div>
      </a-form>
    </a-card>
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
.submit-row { display: flex; justify-content: flex-end; margin-top: 18px; }
@media (max-width: 900px) { .reward-page { padding: 8px; } }
@media (max-width: 640px) {
  .item-editor { align-items: stretch; }
  .item-editor-main { flex-basis: 100%; min-width: 100%; }
  .item-field { flex: 1 1 calc(50% - 10px); min-width: 120px; }
  .submit-row :deep(.arco-btn) { width: 100%; }
}
</style>
