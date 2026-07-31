<!-- 文件: `webui2/src/views/admin/player/PlayerAccounts.vue` -->
<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref} from "vue";
import type {AccountSearchForm} from "../../../api/entitys/AccountSearchForm";
import type {PageResult} from "../../../api/entitys/PageResult";
import type {RechargeOption} from "../../../api/entitys/RechargeOption";
import Request from "../../../api/Request";
import {Message} from "@arco-design/web-vue";
import { openPlayerInspector } from "../../../composables/usePlayerInspector";

const loading = ref(false);

const searchForm = ref<AccountSearchForm>({
  pageNum: 1,
  pageSize: 10
})
const pageResult = ref<PageResult>({
  page: 1,
  totalPageSize: 10,
  totalSize: 0,
  list: []
});
const rechargeOption = ref<RechargeOption>({
  uid: 0,
  accountname: "",
  open: false,
  cera: 0,
  thisCera: 0,
});
const windowHeight = ref(window.innerHeight - 250)
const profilePicker = ref({ open: false, loading: false, accountname: '', roles: [] as any[] });


const search = (resetPage = false) => {
  if (resetPage) {
    searchForm.value.pageNum = 1;
  }

  console.log('Search form:', searchForm.value);
  let url = `api/v1/account?page=${searchForm.value.pageNum}&pageSize=${searchForm.value.pageSize}`;
  url += searchForm.value.account ? `&account=${searchForm.value.account}` : '';
  url += searchForm.value.loginStatus !== undefined && searchForm.value.loginStatus !== '' ?
      `&loginStatus=${searchForm.value.loginStatus}` : '';
  url += searchForm.value.lastLoginTime && searchForm.value.lastLoginTime.length === 2 ?
      `&lastLoginDate=${searchForm.value.lastLoginTime[0]}&lastLoginDateEnd=${searchForm.value.lastLoginTime[1]}` : '';

  loading.value = true;
  Request.get(url).then((response) => {
    console.log('Search response:', response);
    pageResult.value = response.data;

    // 兼容后端返回当前页字段
    if (pageResult.value && typeof (pageResult.value as any).page === "number") {
      searchForm.value.pageNum = (pageResult.value as any).page;
    }
  }).catch((e: any) => {
    Request.showError(e, "查询失败");
  }).finally(() => {
    loading.value = false;
  });
}

const onPageChange = (page: number) => {
  searchForm.value.pageNum = page;
  search(false);
};

const onPageSizeChange = (pageSize: number) => {
  searchForm.value.pageSize = pageSize;
  search(true);
};

/**
 * 充值：打开弹窗并拉取当前余额
 * 说明：沿用旧版接口语义：GET account/{uid} 返回包含 cera 字段
 * 若你后端已统一到 api/v1，请自行替换 URL
 */
const openRecharge = async (uid: number, accountname: string) => {
  rechargeOption.value.uid = uid;
  rechargeOption.value.accountname = accountname;
  rechargeOption.value.cera = 0;
  rechargeOption.value.thisCera = 0;
  rechargeOption.value.open = true;

  try {
    const res = await Request.get(`api/v1/account/${uid}`);
    rechargeOption.value.thisCera = Number(res.data?.cera ?? 0);
  } catch {
    // 拉余额失败也允许继续输入充值
  }
};

/**
 * 充值：提交
 * 说明：沿用旧版接口语义：POST recharge，body=RechargeOption
 * 若你后端已统一到 api/v1，请自行替换 URL
 */
const submitRecharge = async () => {
  if (!rechargeOption.value.uid) return;

  const amount = Number(rechargeOption.value.cera ?? 0);
  if (!Number.isFinite(amount) || amount <= 0) {
    Message.error("请输入大于 0 的充值数额");
    return;
  }

  try {
    await Request.post(`api/v1/recharge`, rechargeOption.value);
    Message.success("充值成功");
    rechargeOption.value.open = false;
    search(false);
  } catch (e: any) {
    Request.showError(e, "充值失败");
  }
};

const openAccountProfile = async (record: any) => {
  profilePicker.value = { open: true, loading: true, accountname: record.accountname || '', roles: [] };
  try {
    const response = await Request.get(`api/v1/charac?page=1&pageSize=100&account=${encodeURIComponent(record.accountname || '')}`);
    profilePicker.value.roles = response.data?.list || (Array.isArray(response.data) ? response.data : []);
    if (profilePicker.value.roles.length === 1) {
      profilePicker.value.open = false;
      openPlayerInspector(Number(profilePicker.value.roles[0].characNo), 'profile', '账号管理');
    }
  } catch (error: any) {
    Request.showError(error, '账号角色读取失败');
  } finally {
    profilePicker.value.loading = false;
  }
};

const chooseProfileRole = (role: any) => {
  profilePicker.value.open = false;
  openPlayerInspector(Number(role.characNo), 'profile', '账号管理');
};


const updateWindowHeight = () => {
  windowHeight.value = window.innerHeight - 250;
};

onMounted(() => {
  updateWindowHeight();
  window.addEventListener('resize', updateWindowHeight);
  search();
});
onBeforeUnmount(() => window.removeEventListener('resize', updateWindowHeight));
</script>

<template>
  <div class="account-manager">
    <a-card>
      <a-form class="gm-filter-form" layout="inline" :model="searchForm">
        <a-form-item label="账号">
          <a-input placeholder="搜索玩家账号" allow-clear v-model="searchForm.account"/>
        </a-form-item>
        <a-form-item label="状态">
          <a-select placeholder="全部状态" style="width: 150px;" allow-clear v-model="searchForm.loginStatus">
            <a-option label="全部状态" value="" />
            <a-option label="在线" value="true" />
            <a-option label="离线" value="false" />
          </a-select>
        </a-form-item>
        <a-form-item label="最后登录时间" >
          <a-range-picker v-model="searchForm.lastLoginTime" />
        </a-form-item>
        <a-form-item class="gm-filter-actions">
          <a-button type="primary" @click="search(true)">搜索</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 账号列表 -->
    <a-table
        scrollbar
        :scroll="{ x: 880, y: windowHeight }"
        style="margin-top: 10px;"
        :data="pageResult.list"
        :loading="loading"
        :pagination="false"
    >
      <template #empty>
        <div style="text-align: center; padding: 20px;">
          暂无数据
        </div>
      </template>
      <template #columns>
        <a-table-column title="账号" data-index="accountname" :width="170" />
        <a-table-column title="在线状态" data-index="loginStatus" :width="110">
          <template #cell="{ record }">
            <a-tag :color="record.loginStatus ? 'green' : 'gray'">
              {{ record.loginStatus ? '在线' : '离线' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="用户身份" data-index="parentUid" :width="130">
          <template #cell="{ record }">
            <a-tag :color="record.parentUid === 0 ? 'green' : 'blue'">
              {{ record.parentUid === 0 ? 'GM管理员' : '玩家' }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="频道号" data-index="channelNo" :width="90">
          <template #cell="{ record }">
          <span>
            {{ record.loginStatus ? record.channelNo : '--' }}
          </span>
          </template>
        </a-table-column>
        <a-table-column title="最后登录时间" data-index="lastLoginDate" :width="190">
          <template #cell="{ record }">
            {{ record.lastLoginDate ? record.lastLoginDate: '从未登录' }}
          </template>
        </a-table-column>
        <a-table-column title="操作" :width="180">
          <template #cell="{ record }">
            <a-space>
              <a-button size="small" @click="openAccountProfile(record)">玩家档案</a-button>
              <a-button size="small" type="primary" @click="openRecharge(record.uid, record.accountname)">充值</a-button>
              <!--            <a-button size="small" status="warning" @click="{}"-->
              <!--            >封号/解封</a-button-->
              <!--            >-->
            </a-space>
          </template>
        </a-table-column>
      </template>
    </a-table>

    <div class="gm-pagination-row">
      <a-pagination
          :current="searchForm.pageNum"
          :page-size="searchForm.pageSize"
          :total="pageResult.totalSize"
          show-total
          show-jumper
          show-page-size
          :page-size-options="[10, 20, 50, 100]"
          @change="onPageChange"
          @page-size-change="onPageSizeChange"
      />
    </div>

    <!-- 充值窗口 -->
    <a-modal
        v-model:visible="rechargeOption.open"
        :title="`为：${rechargeOption.accountname} 充值`"
        :mask-closable="false"
        @ok="submitRecharge"
        @cancel="rechargeOption.open = false"
    >
      <a-form :model="rechargeOption" layout="vertical">
        <a-form-item label="当前余额">
          <a-input :model-value="String(rechargeOption.thisCera)" disabled />
        </a-form-item>
        <a-form-item label="充值数额">
          <a-input-number v-model="rechargeOption.cera" :min="1" :max="9999999" />
        </a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-model:visible="profilePicker.open" :title="`${profilePicker.accountname} 的角色`" :footer="false" :width="'min(620px, calc(100vw - 24px))'" :unmount-on-close="true">
      <a-spin :loading="profilePicker.loading" style="width: 100%">
        <div class="account-role-list">
          <button v-for="role in profilePicker.roles" :key="role.characNo" type="button" @click="chooseProfileRole(role)">
            <span>LV.{{ role.lev ?? role.level ?? '-' }}</span><strong>{{ role.characName || '未知角色' }}</strong><small>#{{ role.characNo }}</small><icon-right />
          </button>
          <a-empty v-if="!profilePicker.loading && !profilePicker.roles.length" description="该账号暂无角色" />
        </div>
      </a-spin>
    </a-modal>
  </div>
</template>

<style scoped lang="less">
.account-manager{
  padding: 16px;
}

.account-role-list { display: grid; gap: 6px; }
.account-role-list button { display: grid; grid-template-columns: 54px minmax(0, 1fr) auto 18px; align-items: center; min-height: 48px; gap: 9px; padding: 7px 10px; border: 1px solid var(--gm-rule); border-radius: 6px; color: var(--gm-text); background: var(--gm-surface-raised); cursor: pointer; text-align: left; }
.account-role-list button:hover { border-color: rgba(77, 228, 210, .65); background: var(--gm-cyan-soft); }
.account-role-list button > span { color: var(--gm-cyan); font: 700 10px/1 ui-monospace, Consolas, monospace; }
.account-role-list button > strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.account-role-list button > small { color: var(--gm-muted); white-space: nowrap; }

@media (max-width: 900px) {
  .account-manager {
    padding: 8px;
  }
}
</style>
