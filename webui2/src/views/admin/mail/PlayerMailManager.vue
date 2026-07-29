<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import Request from '../../../api/Request';

interface CharacterRow {
  characNo: number;
  characName: string;
  accountname?: string;
  mid?: number;
  lev?: number;
}

interface MailboxSummary {
  characNo: number;
  characName: string;
  accountname: string;
  uid: number;
  level: number;
  mailCount: number;
  letterCount: number;
}

const filters = reactive({
  account: '',
  name: '',
  page: 1,
  pageSize: 20
});
const result = ref({ page: 1, pageSize: 20, totalSize: 0, list: [] as CharacterRow[] });
const loading = ref(false);
const mailboxVisible = ref(false);
const mailboxLoading = ref(false);
const clearing = ref(false);
const selectedMailbox = ref<MailboxSummary | null>(null);

const buildCharacterUrl = () => {
  const params = new URLSearchParams({
    page: String(filters.page),
    pageSize: String(filters.pageSize)
  });
  if (filters.account.trim()) params.set('account', filters.account.trim());
  if (filters.name.trim()) params.set('name', filters.name.trim());
  return `/api/v1/charac?${params.toString()}`;
};

const loadCharacters = async (resetPage = false) => {
  if (resetPage) filters.page = 1;
  loading.value = true;
  try {
    const response = await Request.get<any>(buildCharacterUrl());
    result.value = {
      page: Number(response.data?.page || filters.page),
      pageSize: Number(response.data?.totalPageSize || filters.pageSize),
      totalSize: Number(response.data?.totalSize || 0),
      list: response.data?.list || []
    };
    filters.page = result.value.page;
  } catch (error: any) {
    Request.showError(error, '角色查询失败');
  } finally {
    loading.value = false;
  }
};

const resetFilters = () => {
  filters.account = '';
  filters.name = '';
  loadCharacters(true);
};

const openMailbox = async (character: CharacterRow) => {
  mailboxVisible.value = true;
  mailboxLoading.value = true;
  selectedMailbox.value = {
    characNo: character.characNo,
    characName: character.characName,
    accountname: character.accountname || '',
    uid: Number(character.mid || 0),
    level: Number(character.lev || 0),
    mailCount: 0,
    letterCount: 0
  };
  try {
    const response = await Request.get<MailboxSummary>(`/api/v1/gm/mail/player/${character.characNo}`);
    selectedMailbox.value = response.data;
  } catch (error: any) {
    mailboxVisible.value = false;
    Request.showError(error, '邮箱信息读取失败');
  } finally {
    mailboxLoading.value = false;
  }
};

const clearMailbox = () => {
  const mailbox = selectedMailbox.value;
  if (!mailbox || mailbox.mailCount <= 0 || clearing.value) return;
  Modal.confirm({
    title: '确认清空玩家邮箱',
    content: `将永久删除“${mailbox.characName}”（角色 ID ${mailbox.characNo}）的全部 ${mailbox.mailCount} 封邮件。`,
    okText: '删除全部邮件',
    cancelText: '取消',
    okButtonProps: { status: 'danger' },
    onBeforeOk: async () => {
      clearing.value = true;
      try {
        const response = await Request.delete<any>(`/api/v1/gm/mail/player/${mailbox.characNo}`);
        const deleted = Number(response.data?.deletedMailCount || 0);
        selectedMailbox.value = {
          ...mailbox,
          ...response.data
        };
        Message.success(`已清空 ${mailbox.characName} 的邮箱，共删除 ${deleted} 封邮件`);
        return true;
      } catch (error: any) {
        Request.showError(error, '邮箱清理失败');
        return false;
      } finally {
        clearing.value = false;
      }
    }
  });
};

const onPageChange = (page: number) => {
  filters.page = page;
  loadCharacters();
};

const onPageSizeChange = (pageSize: number) => {
  filters.pageSize = pageSize;
  loadCharacters(true);
};

onMounted(() => loadCharacters());
</script>

<template>
  <div class="player-mail-page">
    <a-card class="filter-card">
      <template #title>玩家邮件管理</template>
      <a-form class="gm-filter-form" layout="inline" :model="filters" @submit-success="loadCharacters(true)">
        <a-form-item label="玩家账号">
          <a-input v-model="filters.account" allow-clear placeholder="输入账号" @press-enter="loadCharacters(true)" />
        </a-form-item>
        <a-form-item label="角色名称">
          <a-input v-model="filters.name" allow-clear placeholder="输入角色名" @press-enter="loadCharacters(true)" />
        </a-form-item>
        <a-form-item class="gm-filter-actions">
          <a-space>
            <a-button type="primary" :loading="loading" aria-label="查询角色" @click="loadCharacters(true)">
              <template #icon><icon-search /></template>
              查询
            </a-button>
            <a-button :disabled="loading" @click="resetFilters">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card class="table-card">
      <a-table
        :data="result.list"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 696 }"
        row-key="characNo"
      >
        <template #empty><a-empty description="没有符合条件的角色" /></template>
        <template #columns>
          <a-table-column title="角色 ID" data-index="characNo" :width="78" />
          <a-table-column title="角色名称" :width="200">
            <template #cell="{ record }">
              <div class="character-cell">
                <span class="level-badge">LV.{{ record.lev || 1 }}</span>
                <strong>{{ record.characName }}</strong>
              </div>
            </template>
          </a-table-column>
          <a-table-column title="所属账号" :width="180">
            <template #cell="{ record }">{{ record.accountname || '--' }}</template>
          </a-table-column>
          <a-table-column title="账号 UID" :width="130">
            <template #cell="{ record }">{{ record.mid || '--' }}</template>
          </a-table-column>
          <a-table-column title="操作" :width="108" fixed="right">
            <template #cell="{ record }">
              <a-button
                type="primary"
                size="small"
                :aria-label="`管理 ${record.characName} 的邮箱`"
                @click="openMailbox(record)"
              >
                <template #icon><icon-email /></template>
                管理邮箱
              </a-button>
            </template>
          </a-table-column>
        </template>
      </a-table>
      <div class="gm-pagination-row">
        <a-pagination
          :current="filters.page"
          :page-size="filters.pageSize"
          :total="result.totalSize"
          show-total
          show-jumper
          show-page-size
          :page-size-options="[10, 20, 50, 100]"
          @change="onPageChange"
          @page-size-change="onPageSizeChange"
        />
      </div>
    </a-card>

    <a-modal
      v-model:visible="mailboxVisible"
      :width="'min(620px, calc(100vw - 24px))'"
      :mask-closable="!clearing"
      :closable="!clearing"
      :footer="false"
      :unmount-on-close="true"
      title="玩家邮箱"
    >
      <a-spin class="mailbox-loading" :loading="mailboxLoading">
        <template v-if="selectedMailbox">
          <div class="mailbox-identity">
            <div class="mailbox-icon"><icon-email /></div>
            <div class="identity-copy">
              <strong>{{ selectedMailbox.characName }}</strong>
              <span>{{ selectedMailbox.accountname || '--' }} · 角色 ID {{ selectedMailbox.characNo }}</span>
            </div>
            <span class="modal-level">LV.{{ selectedMailbox.level || 1 }}</span>
          </div>

          <div class="mailbox-metrics">
            <div>
              <span>邮箱邮件</span>
              <strong>{{ selectedMailbox.mailCount }}</strong>
            </div>
            <div>
              <span>关联正文</span>
              <strong>{{ selectedMailbox.letterCount }}</strong>
            </div>
          </div>

          <div class="danger-zone">
            <a-alert type="warning">清空后，邮件及其关联正文将永久删除。</a-alert>
            <a-button
              status="danger"
              long
              :loading="clearing"
              :disabled="mailboxLoading || selectedMailbox.mailCount === 0"
              @click="clearMailbox"
            >
              <template #icon><icon-delete /></template>
              {{ selectedMailbox.mailCount > 0 ? '删除全部邮件' : '邮箱为空' }}
            </a-button>
          </div>
        </template>
      </a-spin>
    </a-modal>
  </div>
</template>

<style scoped lang="less">
.player-mail-page {
  padding: 16px;
}

.table-card {
  margin-top: 12px;
}

.character-cell,
.mailbox-identity {
  display: flex;
  align-items: center;
  min-width: 0;
}

.character-cell {
  gap: 10px;

  strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.level-badge,
.modal-level {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  min-width: 58px;
  height: 28px;
  padding: 0 8px;
  border: 1px solid rgba(77, 228, 210, .72);
  border-radius: 6px;
  color: var(--gm-cyan);
  background: var(--gm-cyan-soft);
  font-size: 12px;
  font-weight: 700;
}

.mailbox-loading {
  display: block;
  width: 100%;
  min-height: 290px;
}

.mailbox-identity {
  gap: 14px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--gm-rule);
}

.mailbox-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 46px;
  width: 46px;
  height: 46px;
  border: 1px solid rgba(77, 228, 210, .52);
  border-radius: 7px;
  color: var(--gm-cyan);
  background: var(--gm-cyan-soft);
  font-size: 22px;
}

.identity-copy {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-width: 0;
  gap: 4px;

  strong,
  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    color: var(--gm-text);
    font-size: 17px;
  }

  span {
    color: var(--gm-muted);
    font-size: 13px;
  }
}

.mailbox-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  padding: 18px 0;

  > div {
    display: flex;
    flex-direction: column;
    min-width: 0;
    padding: 16px;
    border: 1px solid var(--gm-rule);
    border-radius: 7px;
    background: var(--gm-surface-raised);
  }

  span {
    color: var(--gm-muted);
    font-size: 13px;
  }

  strong {
    margin-top: 8px;
    color: var(--gm-text);
    font-size: 28px;
    line-height: 1;
  }
}

.danger-zone {
  display: grid;
  gap: 12px;
  padding-top: 18px;
  border-top: 1px solid var(--gm-rule);

  :deep(.arco-alert) {
    border-color: rgba(255, 188, 104, .42);
    background: rgba(255, 188, 104, .12);
  }

  :deep(.arco-alert-content),
  :deep(.arco-alert-icon) {
    color: #ffd59a;
  }
}

@media (max-width: 900px) {
  .player-mail-page {
    padding: 8px;
  }
}

@media (max-width: 480px) {
  .mailbox-identity {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .identity-copy {
    flex-basis: calc(100% - 60px);
  }

  .modal-level {
    margin-left: 60px;
  }

  .mailbox-metrics {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
