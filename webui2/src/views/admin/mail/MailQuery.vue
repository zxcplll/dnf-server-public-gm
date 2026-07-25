<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Message } from '@arco-design/web-vue';
import Request from '../../../api/Request';
import ItemPicker from '../../../components/ItemPicker.vue';
import ItemImg from '../../../components/ItemImg.vue';

const loading = ref(false);
const filters = reactive({
  sender: '',
  receiver: '',
  itemId: undefined as number | undefined,
  keyword: '',
  page: 1,
  pageSize: 20
});
const itemFilter = ref<any | null>(null);
const result = ref({ page: 1, pageSize: 20, totalSize: 0, list: [] as any[] });

const buildUrl = () => {
  const params = new URLSearchParams();
  if (filters.sender.trim()) params.set('sender', filters.sender.trim());
  if (filters.receiver.trim()) params.set('receiver', filters.receiver.trim());
  if (filters.itemId) params.set('itemId', String(filters.itemId));
  if (filters.keyword.trim()) params.set('keyword', filters.keyword.trim());
  params.set('page', String(filters.page));
  params.set('pageSize', String(filters.pageSize));
  return `/api/v1/gm/mail?${params.toString()}`;
};

const load = async (resetPage = false) => {
  if (resetPage) filters.page = 1;
  loading.value = true;
  try {
    const response = await Request.get<any>(buildUrl());
    result.value = {
      page: response.data?.page || filters.page,
      pageSize: response.data?.totalPageSize || filters.pageSize,
      totalSize: response.data?.totalSize || 0,
      list: response.data?.list || []
    };
    filters.page = result.value.page;
  } catch (error: any) {
    Message.error(error?.message || '邮件查询失败');
  } finally {
    loading.value = false;
  }
};

const clearFilters = () => {
  filters.sender = '';
  filters.receiver = '';
  filters.itemId = undefined;
  filters.keyword = '';
  itemFilter.value = null;
  load(true);
};

const onPageChange = (page: number) => {
  filters.page = page;
  load();
};

const onPageSizeChange = (pageSize: number) => {
  filters.pageSize = pageSize;
  load(true);
};

const text = (value: any, fallback = '--') => {
  if (value === null || value === undefined || value === '') return fallback;
  if (Array.isArray(value)) return value.join('-');
  return String(value);
};

onMounted(load);
</script>

<template>
  <div class="mail-page">
    <a-card>
      <template #title>邮件查询</template>
      <a-form layout="inline" :model="filters">
        <a-form-item label="发件人"><a-input v-model="filters.sender" allow-clear placeholder="GM后台或角色名" /></a-form-item>
        <a-form-item label="收件人"><a-input v-model="filters.receiver" allow-clear placeholder="角色名或角色UID" /></a-form-item>
        <a-form-item label="道具"><ItemPicker v-model="itemFilter" width="220" @change="(item) => filters.itemId = item?.id" /></a-form-item>
        <a-form-item label="相关信息"><a-input v-model="filters.keyword" allow-clear placeholder="邮件ID、正文或发件人" /></a-form-item>
        <a-form-item>
          <a-space><a-button type="primary" @click="load(true)">查询</a-button><a-button @click="clearFilters">重置</a-button></a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card class="table-card">
      <a-table :data="result.list" :loading="loading" :pagination="false" row-key="postalId" :scroll="{ x: 1500 }">
        <template #columns>
          <a-table-column title="邮件ID" data-index="postalId" :width="110" />
          <a-table-column title="发件人" :width="160">
            <template #cell="{ record }"><span>{{ text(record.sendCharacName) }}</span><a-tag size="small" color="arcoblue" class="sender-tag">{{ text(record.senderType) }}</a-tag></template>
          </a-table-column>
          <a-table-column title="收件角色" :width="170">
            <template #cell="{ record }">{{ text(record.receiverName, text(record.receiveCharacNo)) }}</template>
          </a-table-column>
          <a-table-column title="UID" data-index="receiverUid" :width="100" />
          <a-table-column title="物品" :width="230">
            <template #cell="{ record }">
              <div v-if="record.itemId" class="item-cell">
                <ItemImg v-if="record.itemIcon" :icon="record.itemIcon" :rarity="record.itemRarity || 0" />
                <span>{{ text(record.itemName, `道具 ${record.itemId}`) }}</span><span class="item-id">#{{ record.itemId }}</span>
              </div>
              <span v-else>无道具</span>
            </template>
          </a-table-column>
          <a-table-column title="数量" data-index="addInfo" :width="80" />
          <a-table-column title="金币" data-index="gold" :width="100" />
          <a-table-column title="强化/锻造" :width="110">
            <template #cell="{ record }">+{{ record.upgrade || 0 }} / +{{ record.seperateUpgrade || 0 }}</template>
          </a-table-column>
          <a-table-column title="红字/封装" :width="110">
            <template #cell="{ record }">{{ record.amplifyOption ? `属性 ${record.amplifyValue || 0}` : '无' }} / {{ record.sealFlag ? '是' : '否' }}</template>
          </a-table-column>
          <a-table-column title="发送时间" :width="170"><template #cell="{ record }">{{ text(record.occTime) }}</template></a-table-column>
          <a-table-column title="接收时间" :width="170"><template #cell="{ record }">{{ text(record.receiveTime) }}</template></a-table-column>
          <a-table-column title="是否领取" :width="95"><template #cell="{ record }"><a-tag :color="record.claimed ? 'green' : 'gray'">{{ record.claimed ? '已领取' : '未领取' }}</a-tag></template></a-table-column>
        </template>
      </a-table>
      <div class="pagination-row"><a-pagination :current="filters.page" :page-size="filters.pageSize" :total="result.totalSize" show-total show-jumper show-page-size :page-size-options="[20, 50, 100]" @change="onPageChange" @page-size-change="onPageSizeChange" /></div>
    </a-card>
  </div>
</template>

<style scoped lang="less">
.mail-page { padding: 16px; }
.table-card { margin-top: 12px; }
.pagination-row { display: flex; justify-content: flex-end; margin-top: 14px; }
.item-cell { display: flex; align-items: center; gap: 8px; min-width: 0; }
.item-cell span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-id { color: var(--color-text-3); font-size: 11px; }
.sender-tag { margin-left: 4px; flex: none; }
@media (max-width: 900px) { .mail-page { padding: 8px; } }
</style>
