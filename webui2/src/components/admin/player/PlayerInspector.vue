<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import ItemImg from '../../ItemImg.vue';
import Request from '../../../api/Request';
import {
  gmOperations,
  type ContractRecord,
  type CurrencyBalance,
  type InventorySlot,
  type PlayerInventory,
  type PlayerProfile,
} from '../../../api/gmOperations';
import { closePlayerInspector, usePlayerInspector, type PlayerInspectorTab } from '../../../composables/usePlayerInspector';

const { state } = usePlayerInspector();
const visible = computed({ get: () => state.visible, set: (value) => { if (!value) closePlayerInspector(); } });
const activeTab = ref<PlayerInspectorTab>('profile');
const profile = ref<PlayerProfile | null>(null);
const balances = ref<CurrencyBalance[]>([]);
const contracts = ref<ContractRecord[]>([]);
const inventory = ref<PlayerInventory>({ bags: [] });
const mailRewards = ref<Record<string, any>>({});
const guildActivity = ref<Record<string, any>>({});
const audit = ref({ page: 1, pageSize: 100, totalSize: 0, list: [] as Record<string, any>[], security: null as Record<string, any> | null });
const contractOptionsData = ref<Array<{ value: string; label: string }>>([]);
const loaded = reactive<Record<PlayerInspectorTab, boolean>>({
  profile: false,
  currency: false,
  contracts: false,
  inventory: false,
  mail: false,
  guild: false,
  security: false,
});
const loading = reactive<Record<PlayerInspectorTab, boolean>>({ ...loaded });
const errors = reactive<Record<PlayerInspectorTab, string>>({
  profile: '', currency: '', contracts: '', inventory: '', mail: '', guild: '', security: '',
});
const currencyDelta = reactive<Record<string, number>>({});
const contractType = ref('10');
const contractDays = ref(7);
let loadSequence = 0;

const numberFormatter = new Intl.NumberFormat('zh-CN');
const money = (value: unknown) => numberFormatter.format(Number(value || 0));
const moneyOrUnavailable = (value: unknown) => value === null || value === undefined ? '--' : money(value);
const text = (value: unknown, fallback = '--') => value === undefined || value === null || value === '' ? fallback : String(value);
const requestId = () => globalThis.crypto?.randomUUID?.() || `gm-${Date.now()}-${Math.random().toString(16).slice(2)}`;

const profileTitle = computed(() => profile.value?.characName || (state.characNo ? `角色 ${state.characNo}` : '玩家档案'));
const runtimeAvailable = computed(() => {
  const runtime = profile.value?.runtime;
  return Boolean(profile.value?.online) && runtime?.status === 'AVAILABLE';
});
const runtimeUnavailableMessage = computed(() =>
  profile.value?.runtime?.reason || '角色离线或 Frida 桥接暂不可用');
const runtimeBoolean = (value: unknown, truthy: string, falsy: string) =>
  value === true ? truthy : value === false ? falsy : '--';
const profileEntries = computed(() => {
  const row = profile.value || ({} as PlayerProfile);
  return [
    ['账号', row.accountName || row.accountname], ['账号 UID', row.uid ?? row.memberId],
    ['角色编号', row.characNo || state.characNo], ['角色等级', row.level ?? row.lev],
    ['职业', row.jobName || row.job], ['转职', row.growTypeName || row.growType],
    ['创建时间', row.createTime], ['最后登录', row.lastLoginTime || row.lastPlayTime],
  ];
});
const runtimeEntries = computed(() => {
  const runtime = profile.value?.runtime || {};
  return [
    ['当前金币', runtime.gold], ['疲劳值', runtime.fatigue], ['位置', runtime.mapName || runtime.mapId],
    ['队伍', runtimeBoolean(runtime.party, '组队中', '未组队')], ['公会', runtime.guildName],
    ['交易状态', runtimeBoolean(runtime.trading, '交易中', '空闲')],
  ];
});
const inventoryGroups = computed(() => {
  if (Array.isArray(inventory.value?.bags)) return inventory.value.bags;
  const source = inventory.value as any;
  const groups: Array<[string, string]> = [
    ['equipment', '已穿戴装备'], ['inventory', '角色背包'], ['creatures', '宠物'], ['accountVault', '账号仓库'],
  ];
  return groups.map(([key, label]) => ({ key, label, slots: Array.isArray(source[key]) ? source[key] : [] }))
    .filter((group) => group.slots.length);
});
const mailEntries = computed(() => Object.entries(mailRewards.value || {}).filter(([, value]) => !Array.isArray(value) && typeof value !== 'object'));
const guildEntries = computed(() => {
  const base = Object.entries(guildActivity.value || {}).filter(([, value]) => !Array.isArray(value) && typeof value !== 'object');
  const membership = guildActivity.value?.membership;
  return membership && typeof membership === 'object' ? [...base, ...Object.entries(membership)] : base;
});
const mailRows = computed(() => (mailRewards.value?.recentRewards || mailRewards.value?.list || mailRewards.value?.records || []) as Record<string, any>[]);
const activityRows = computed(() => (guildActivity.value?.activity || guildActivity.value?.list || guildActivity.value?.records || []) as Record<string, any>[]);
const contractOptions = computed(() => {
  if (contractOptionsData.value.length) return contractOptionsData.value;
  const supported = (profile.value as any)?.supportedContracts;
  if (Array.isArray(supported) && supported.length) return supported;
  return [
    { value: '9', label: '成长之契约' },
    { value: '10', label: '霸王之契约' },
    { value: '11', label: '达人之契约' },
    { value: '22', label: '晶体契约' },
  ];
});

const resetState = () => {
  loadSequence += 1;
  profile.value = null;
  balances.value = [];
  contracts.value = [];
  inventory.value = { bags: [] };
  mailRewards.value = {};
  guildActivity.value = {};
  audit.value = { page: 1, pageSize: 100, totalSize: 0, list: [], security: null };
  contractOptionsData.value = [];
  Object.keys(loaded).forEach((key) => {
    loaded[key as PlayerInspectorTab] = false;
    loading[key as PlayerInspectorTab] = false;
    errors[key as PlayerInspectorTab] = '';
  });
  Object.keys(currencyDelta).forEach((key) => delete currencyDelta[key]);
};

const loadTab = async (tab: PlayerInspectorTab, force = false) => {
  if (!state.visible || !state.characNo || loading[tab] || (loaded[tab] && !force)) return;
  const sequence = loadSequence;
  loading[tab] = true;
  errors[tab] = '';
  try {
    if (tab === 'profile') profile.value = await gmOperations.getPlayerProfile(state.characNo);
    if (tab === 'currency') balances.value = (await gmOperations.getPlayerCurrency(state.characNo))?.balances || [];
    if (tab === 'contracts') {
      const payload = await gmOperations.getPlayerContracts(state.characNo);
      contracts.value = payload?.contracts || [];
      contractOptionsData.value = payload?.options || [];
      if (!contractOptionsData.value.some((item) => item.value === contractType.value)) contractType.value = contractOptionsData.value[0]?.value || '10';
    }
    if (tab === 'inventory') inventory.value = await gmOperations.getPlayerInventory(state.characNo) || { bags: [] };
    if (tab === 'mail') mailRewards.value = await gmOperations.getPlayerMailRewards(state.characNo) || {};
    if (tab === 'guild') guildActivity.value = await gmOperations.getPlayerGuildActivity(state.characNo) || {};
    if (tab === 'security') audit.value = await gmOperations.getPlayerSecurityAudit(state.characNo, { page: audit.value.page, pageSize: audit.value.pageSize });
    if (sequence === loadSequence) loaded[tab] = true;
  } catch (error: any) {
    if (sequence === loadSequence) errors[tab] = error?.response?.data?.message || error?.message || '数据读取失败';
  } finally {
    if (sequence === loadSequence) loading[tab] = false;
  }
};

const refreshCurrent = () => loadTab(activeTab.value, true);
const canChangeCurrency = (row: CurrencyBalance) => {
  const delta = Number(currencyDelta[row.type] || 0);
  if (!row.editable || !Number.isFinite(delta) || delta === 0) return false;
  if (profile.value?.online && row.onlineAllowed === false) return false;
  return delta >= Number(row.minDelta ?? -4294967295) && delta <= Number(row.maxDelta ?? 4294967295);
};

const submitCurrency = (row: CurrencyBalance) => {
  if (!profile.value || !canChangeCurrency(row)) return;
  const delta = Number(currencyDelta[row.type]);
  Modal.confirm({
    modalClass: 'player-operation-confirm', zIndex: 1300,
    title: `调整 ${row.label || row.type}`,
    content: `${profileTitle.value}：${money(row.balance)} ${delta > 0 ? '+' : '-'} ${money(Math.abs(delta))}`,
    okText: '确认调整', cancelText: '取消', maskClosable: false, escToClose: false,
    onBeforeOk: async () => {
      try {
        const result = await gmOperations.changeCurrency(state.characNo, {
          type: row.type,
          delta,
          expectedBalance: row.balance,
          requestId: requestId(),
        });
        currencyDelta[row.type] = 0;
        await Promise.all([loadTab('profile', true), loadTab('currency', true)]);
        Message.success(`货币已更新${result.auditId ? `，审计 ${result.auditId}` : ''}`);
        return true;
      } catch (error: any) {
        RequestError(error, '货币调整失败');
        return false;
      }
    },
  } as any);
};

const RequestError = (error: any, fallback: string) => {
  Request.showError(error, fallback);
};

const submitContract = (action: 'grant' | 'extend' | 'shorten' | 'revoke', row?: ContractRecord) => {
  if (!profile.value) return;
  const type = row?.type || contractType.value;
  const label = row?.label || contractOptions.value.find((item: any) => item.value === type)?.label || type;
  const days = action === 'revoke' ? 0 : Number(contractDays.value || 0);
  Modal.confirm({
    modalClass: 'player-operation-confirm', zIndex: 1300,
    title: `${action === 'grant' ? '发放' : action === 'extend' ? '延长' : action === 'shorten' ? '缩短' : '撤销'}契约`,
    content: `${profileTitle.value} · ${label}${days ? ` · ${days} 天` : ''}`,
    okText: '确认执行', cancelText: '取消', maskClosable: false, escToClose: false,
    onBeforeOk: async () => {
      try {
        await gmOperations.changePlayerContract(state.characNo, {
          action, type, preType: Number(type), eventId: row?.eventId, days, requestId: requestId(),
        });
        await Promise.all([loadTab('profile', true), loadTab('contracts', true)]);
        Message.success(profile.value?.online ? '契约已更新，在线角色重新登录后生效' : '契约已更新');
        return true;
      } catch (error: any) {
        RequestError(error, '契约操作失败');
        return false;
      }
    },
  } as any);
};

const slotTitle = (slot: InventorySlot) => slot.unavailable
  ? `槽位 ${slot.slot}：${slot.reason || '无法解析'}`
  : `${slot.itemName || `物品 ${slot.itemId || '-'}`} · 数量 ${slot.quantity || 1}`;

watch(() => [state.visible, state.characNo] as const, ([isVisible]) => {
  if (!isVisible) return;
  resetState();
  activeTab.value = state.initialTab;
  loadTab('profile');
  if (activeTab.value !== 'profile') loadTab(activeTab.value);
});
watch(activeTab, (tab) => loadTab(tab));
</script>

<template>
  <a-drawer
    v-model:visible="visible"
    class="player-inspector-drawer"
    :width="'min(1180px, 100vw)'"
    :footer="false"
    :z-index="1200"
    :mask-closable="false"
    :unmount-on-close="true"
  >
    <template #title>
      <div class="inspector-title">
        <span class="inspector-avatar">LV.{{ profile?.level ?? profile?.lev ?? '-' }}</span>
        <div><strong>{{ profileTitle }}</strong><small>角色 {{ state.characNo }}<template v-if="profile?.uid"> · UID {{ profile.uid }}</template></small></div>
        <a-tag :color="profile?.online ? 'green' : 'gray'">{{ profile?.online ? '实时在线' : '离线资料' }}</a-tag>
      </div>
    </template>

    <div class="player-inspector">
      <a-alert v-if="profile && !runtimeAvailable" type="warning" show-icon class="runtime-alert">
        运行时资料当前不可用，数据库档案仍可正常查看。
      </a-alert>
      <div class="inspector-toolbar">
        <span>来源：{{ state.source || '后台管理' }}</span>
        <a-button size="small" :loading="loading[activeTab]" @click="refreshCurrent"><template #icon><icon-refresh /></template>刷新当前标签</a-button>
      </div>

      <a-tabs v-model:active-key="activeTab" class="inspector-tabs" lazy-load>
        <a-tab-pane key="profile" title="基本资料">
          <a-spin :loading="loading.profile" class="tab-spin">
            <a-alert v-if="errors.profile" type="error" :show-icon="true">{{ errors.profile }}</a-alert>
            <template v-else-if="profile">
              <section class="data-section">
                <h3>数据库档案</h3>
                <div class="kv-grid"><div v-for="entry in profileEntries" :key="entry[0]"><span>{{ entry[0] }}</span><strong>{{ text(entry[1]) }}</strong></div></div>
              </section>
              <section class="data-section">
                <h3>游戏运行时</h3>
                <div v-if="runtimeAvailable" class="kv-grid"><div v-for="entry in runtimeEntries" :key="entry[0]"><span>{{ entry[0] }}</span><strong>{{ text(entry[1]) }}</strong></div></div>
                <a-empty v-else :description="runtimeUnavailableMessage" />
              </section>
            </template>
          </a-spin>
        </a-tab-pane>

        <a-tab-pane key="currency" title="货币">
          <a-spin :loading="loading.currency" class="tab-spin">
            <a-alert v-if="errors.currency" type="error" show-icon>{{ errors.currency }}</a-alert>
            <a-table v-else :data="balances" :pagination="false" :scroll="{ x: 820 }" row-key="type">
              <template #empty><a-empty description="暂无货币资料" /></template>
              <template #columns>
                <a-table-column title="货币" :width="150"><template #cell="{ record }"><strong>{{ record.label || record.type }}</strong><small class="block-muted">{{ record.type }}</small></template></a-table-column>
                <a-table-column title="当前余额" :width="170"><template #cell="{ record }"><span class="numeric">{{ moneyOrUnavailable(record.balance) }}</span></template></a-table-column>
                <a-table-column title="增加 / 扣减" :width="220"><template #cell="{ record }"><a-input-number v-model="currencyDelta[record.type]" :min="record.minDelta ?? -4294967295" :max="record.maxDelta ?? 4294967295" hide-button /></template></a-table-column>
                <a-table-column title="限制" :width="190"><template #cell="{ record }"><a-tag :color="record.editable ? 'green' : 'gray'">{{ record.editable ? '允许修改' : '只读' }}</a-tag><small class="block-muted">{{ profile?.online && record.onlineAllowed === false ? '需角色离线后操作' : record.reason || '带余额校验' }}</small></template></a-table-column>
                <a-table-column title="操作" :width="90" fixed="right"><template #cell="{ record }"><a-tooltip content="按差值调整货币"><a-button type="primary" shape="circle" :disabled="!canChangeCurrency(record)" @click="submitCurrency(record)"><icon-swap /></a-button></a-tooltip></template></a-table-column>
              </template>
            </a-table>
          </a-spin>
        </a-tab-pane>

        <a-tab-pane key="contracts" title="契约">
          <a-spin :loading="loading.contracts" class="tab-spin">
            <a-alert v-if="errors.contracts" type="error" show-icon>{{ errors.contracts }}</a-alert>
            <div v-else class="contract-toolbar">
              <a-select v-model="contractType" placeholder="选择契约" :trigger-props="{ contentClass: 'player-inspector-popup' }"><a-option v-for="option in contractOptions" :key="option.value" :value="option.value">{{ option.label }}</a-option></a-select>
              <a-input-number v-model="contractDays" :min="1" :max="366" :precision="0"><template #suffix>天</template></a-input-number>
              <a-button type="primary" @click="submitContract('grant')"><template #icon><icon-plus /></template>发放契约</a-button>
            </div>
            <a-table v-if="!errors.contracts" :data="contracts" :pagination="false" :scroll="{ x: 900 }" row-key="type">
              <template #empty><a-empty description="暂无契约记录" /></template>
              <template #columns>
                <a-table-column title="契约" :width="180"><template #cell="{ record }"><strong>{{ record.label || record.type }}</strong><small class="block-muted">{{ record.type }}</small></template></a-table-column>
                <a-table-column title="起止时间" :width="300"><template #cell="{ record }">{{ text(record.startAt) }}<span class="range-separator">至</span>{{ text(record.endAt) }}</template></a-table-column>
                <a-table-column title="数据库状态" :width="120"><template #cell="{ record }"><a-tag :color="record.active ? 'green' : 'gray'">{{ record.active ? '生效中' : '已过期' }}</a-tag></template></a-table-column>
                <a-table-column title="运行时" :width="130"><template #cell="{ record }"><a-tag :color="record.runtimeActive === true ? 'green' : record.runtimeActive === false ? 'red' : 'gray'">{{ record.runtimeActive === true ? '已生效' : record.runtimeActive === false ? '未生效' : '不可用' }}</a-tag></template></a-table-column>
                <a-table-column title="操作" :width="210" fixed="right"><template #cell="{ record }"><a-space><a-button size="small" @click="submitContract('extend', record)">延长</a-button><a-button size="small" @click="submitContract('shorten', record)">缩短</a-button><a-button size="small" status="danger" @click="submitContract('revoke', record)">撤销</a-button></a-space></template></a-table-column>
              </template>
            </a-table>
          </a-spin>
        </a-tab-pane>

        <a-tab-pane key="inventory" title="背包与装备">
          <a-spin :loading="loading.inventory" class="tab-spin">
            <a-alert v-if="errors.inventory" type="error" show-icon>{{ errors.inventory }}</a-alert>
            <a-alert v-else-if="inventory.warning" type="warning" show-icon>{{ inventory.warning }}</a-alert>
            <div v-if="!errors.inventory && inventoryGroups.length" class="inventory-groups">
              <section v-for="group in inventoryGroups" :key="group.key" class="inventory-group">
                <div class="section-heading"><h3>{{ group.label }}</h3><span>{{ group.slots.length }} 个槽位</span></div>
                <div class="slot-grid">
                  <a-tooltip v-for="slot in group.slots" :key="`${group.key}-${slot.slot}`" :content="slotTitle(slot)">
                    <div class="inventory-slot" :class="{ unavailable: slot.unavailable, empty: !slot.itemId }" tabindex="0">
                      <span class="slot-index">{{ slot.slot }}</span>
                      <ItemImg v-if="slot.itemId && slot.icon" :icon="slot.icon" :rarity="slot.rarity || 0" />
                      <div class="slot-copy"><strong>{{ slot.unavailable ? '无法解析' : slot.itemName || (slot.itemId ? `物品 ${slot.itemId}` : '空槽位') }}</strong><small v-if="slot.itemId">#{{ slot.itemId }} · {{ slot.quantity || 1 }} 个</small><small v-else>{{ slot.reason || '未放置物品' }}</small></div>
                    </div>
                  </a-tooltip>
                </div>
              </section>
            </div>
            <a-empty v-else-if="!loading.inventory && !errors.inventory" description="暂无可读取的背包与装备资料" />
          </a-spin>
        </a-tab-pane>

        <a-tab-pane key="mail" title="邮件与奖励">
          <a-spin :loading="loading.mail" class="tab-spin">
            <a-alert v-if="errors.mail" type="error" show-icon>{{ errors.mail }}</a-alert>
            <div v-else class="summary-layout">
              <div class="kv-grid compact"><div v-for="entry in mailEntries" :key="entry[0]"><span>{{ entry[0] }}</span><strong>{{ text(entry[1]) }}</strong></div></div>
              <a-table :data="mailRows" :pagination="false" :scroll="{ x: 760 }">
                <template #empty><a-empty description="暂无邮件或奖励摘要" /></template>
                <template #columns>
                  <a-table-column title="发放时间" data-index="awardedAt" :width="190" />
                  <a-table-column title="间隔" :width="100"><template #cell="{ record }">{{ record.intervalMinutes ?? '--' }} 分钟</template></a-table-column>
                  <a-table-column title="金币" :width="140"><template #cell="{ record }"><span class="numeric">{{ money(record.gold) }}</span></template></a-table-column>
                  <a-table-column title="点券" :width="120"><template #cell="{ record }">{{ money(record.ceraPoint) }}</template></a-table-column>
                  <a-table-column title="状态" :width="110"><template #cell="{ record }"><a-tag :color="String(record.status).toUpperCase().includes('FAIL') ? 'red' : 'green'">{{ record.status || '--' }}</a-tag></template></a-table-column>
                  <a-table-column title="说明" data-index="message" :width="220" ellipsis tooltip />
                </template>
              </a-table>
            </div>
          </a-spin>
        </a-tab-pane>

        <a-tab-pane key="guild" title="公会与活动">
          <a-spin :loading="loading.guild" class="tab-spin">
            <a-alert v-if="errors.guild" type="error" show-icon>{{ errors.guild }}</a-alert>
            <div v-else class="summary-layout">
              <div class="kv-grid compact"><div v-for="entry in guildEntries" :key="entry[0]"><span>{{ entry[0] }}</span><strong>{{ text(entry[1]) }}</strong></div></div>
              <a-table :data="activityRows" :pagination="false" :scroll="{ x: 760 }">
                <template #empty><a-empty description="暂无公会或活动记录" /></template>
                <template #columns>
                  <a-table-column title="日期" data-index="date" :width="150" />
                  <a-table-column title="在线时长" :width="130"><template #cell="{ record }">{{ money(record.playTime) }} 秒</template></a-table-column>
                  <a-table-column title="登录次数" data-index="playCount" :width="120" />
                  <a-table-column title="交易次数" data-index="tradeCount" :width="120" />
                  <a-table-column title="获得经验" :width="150"><template #cell="{ record }">{{ money(record.exp) }}</template></a-table-column>
                  <a-table-column title="消耗疲劳" data-index="usedFatigue" :width="120" />
                </template>
              </a-table>
            </div>
          </a-spin>
        </a-tab-pane>

        <a-tab-pane key="security" title="安全信息">
          <a-spin :loading="loading.security" class="tab-spin">
            <a-alert v-if="errors.security" type="error" show-icon>{{ errors.security }}</a-alert>
            <template v-else>
            <div v-if="audit.security" class="kv-grid compact security-grid">
              <div><span>账号</span><strong>{{ text(audit.security.accountname) }}</strong></div>
              <div><span>账号 UID</span><strong>{{ text(audit.security.accountId) }}</strong></div>
              <div><span>最近 IP</span><strong>{{ text(audit.security.loginIp || audit.security.ip) }}</strong></div>
              <div><span>最近设备</span><strong>{{ text(audit.security.loginMac) }}</strong></div>
              <div><span>封禁 IP</span><strong>{{ text(audit.security.sealIp) }}</strong></div>
              <div><span>封禁设备</span><strong>{{ text(audit.security.sealMac) }}</strong></div>
            </div>
            <a-table :data="audit.list" :pagination="false" :scroll="{ x: 980 }">
              <template #empty><a-empty description="暂无安全信息或后台操作记录" /></template>
              <template #columns>
                <a-table-column title="时间" data-index="createdAt" :width="180" />
                <a-table-column title="管理员" data-index="operatorName" :width="140" />
                <a-table-column title="模块" data-index="module" :width="120" />
                <a-table-column title="操作" data-index="action" :width="160" />
                <a-table-column title="请求编号" data-index="requestId" :width="220" ellipsis tooltip />
                <a-table-column title="结果" :width="110"><template #cell="{ record }"><a-tag :color="String(record.status).toUpperCase().includes('FAIL') ? 'red' : 'green'">{{ record.status || '--' }}</a-tag></template></a-table-column>
                <a-table-column title="说明" data-index="message" :width="220" ellipsis tooltip />
              </template>
            </a-table>
            </template>
          </a-spin>
        </a-tab-pane>
      </a-tabs>
    </div>
  </a-drawer>
</template>

<style scoped lang="less">
.player-inspector { min-width: 0; color: var(--gm-text); }
.inspector-title { display: flex; align-items: center; min-width: 0; gap: 10px; }
.inspector-title > div { min-width: 0; }
.inspector-title strong, .inspector-title small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.inspector-title strong { color: var(--gm-text); font-size: 16px; }
.inspector-title small { margin-top: 2px; color: var(--gm-muted); font-size: 11px; }
.inspector-avatar { display: inline-flex; align-items: center; justify-content: center; flex: 0 0 54px; height: 32px; border: 1px solid rgba(77, 228, 210, .65); border-radius: 6px; color: var(--gm-cyan); background: var(--gm-cyan-soft); font: 700 11px/1 ui-monospace, Consolas, monospace; }
.runtime-alert { margin-bottom: 10px; }
.inspector-toolbar { display: flex; align-items: center; justify-content: space-between; min-width: 0; margin-bottom: 4px; color: var(--gm-muted); font-size: 12px; }
.inspector-tabs { min-width: 0; }
.tab-spin { display: block; width: 100%; min-height: 260px; }
.data-section { margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--gm-rule); }
.data-section:first-child { margin-top: 0; padding-top: 0; border-top: 0; }
.data-section h3, .section-heading h3 { margin: 0; color: var(--gm-text); font-size: 14px; }
.kv-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-top: 12px; border-top: 1px solid var(--gm-rule); border-left: 1px solid var(--gm-rule); }
.kv-grid > div { min-width: 0; min-height: 68px; padding: 10px 12px; border-right: 1px solid var(--gm-rule); border-bottom: 1px solid var(--gm-rule); background: rgba(21, 34, 56, .45); }
.kv-grid span, .kv-grid strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kv-grid span { color: var(--gm-muted); font-size: 11px; }
.kv-grid strong { margin-top: 7px; color: var(--gm-text); font-size: 14px; }
.kv-grid.compact { grid-template-columns: repeat(3, minmax(0, 1fr)); margin-bottom: 14px; }
.block-muted { display: block; margin-top: 4px; color: var(--gm-muted); font-size: 11px; }
.numeric { color: var(--gm-amber); font: 700 14px/1.2 ui-monospace, Consolas, monospace; }
.contract-toolbar { display: grid; grid-template-columns: minmax(180px, 1fr) 150px auto; gap: 8px; margin-bottom: 12px; }
.range-separator { margin: 0 8px; color: var(--gm-subtle); }
.inventory-groups { display: grid; gap: 18px; }
.inventory-group { min-width: 0; }
.section-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.section-heading span { color: var(--gm-muted); font-size: 11px; }
.slot-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(168px, 1fr)); gap: 6px; }
.inventory-slot { position: relative; display: grid; grid-template-columns: 40px minmax(0, 1fr); align-items: center; min-width: 0; min-height: 58px; gap: 8px; padding: 8px; border: 1px solid var(--gm-rule); border-radius: 6px; background: var(--gm-surface-raised); }
.inventory-slot:focus-visible { outline: 2px solid var(--gm-cyan); outline-offset: 1px; }
.inventory-slot.empty { opacity: .62; }
.inventory-slot.unavailable { border-color: rgba(255, 125, 141, .38); }
.slot-index { position: absolute; top: 3px; right: 5px; color: var(--gm-subtle); font-size: 9px; }
.slot-copy { min-width: 0; }
.slot-copy strong, .slot-copy small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.slot-copy strong { color: var(--gm-text); font-size: 12px; }
.slot-copy small { margin-top: 4px; color: var(--gm-muted); font-size: 10px; }
.summary-layout { min-width: 0; }

:global(.player-inspector-drawer .arco-drawer) { border-left: 1px solid rgba(77, 228, 210, .26); background: var(--gm-surface); color: var(--gm-text); box-shadow: -22px 0 70px rgba(0, 0, 0, .5); }
:global(.player-inspector-drawer .arco-drawer-header) { border-bottom-color: var(--gm-rule); background: #0a111d; }
:global(.player-inspector-drawer .arco-drawer-body) { padding: 14px 18px 20px; background: var(--gm-surface); }
:global(.player-inspector-drawer .arco-alert) { border-color: rgba(255, 188, 104, .36); background: rgba(118, 73, 19, .22); color: #f5d19b; }
:global(.player-inspector-drawer .arco-alert-warning .arco-alert-icon) { color: var(--gm-amber); }
:global(.player-inspector-drawer .arco-alert-error) { border-color: rgba(255, 125, 141, .4); background: rgba(122, 37, 52, .24); color: #ffc1ca; }
:global(.player-inspector-drawer .arco-alert-error .arco-alert-icon) { color: var(--gm-red); }
:global(.player-inspector-drawer .arco-tabs-nav::before) { background: var(--gm-rule); }
:global(.player-inspector-drawer .arco-tabs-tab) { color: var(--gm-muted); }
:global(.player-inspector-drawer .arco-tabs-tab-active),
:global(.player-inspector-drawer .arco-tabs-tab:hover) { color: var(--gm-cyan); }

@media (max-width: 760px) {
  .inspector-toolbar { align-items: stretch; flex-direction: column; gap: 8px; }
  .inspector-toolbar .arco-btn { width: 100%; }
  .kv-grid, .kv-grid.compact { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .contract-toolbar { grid-template-columns: minmax(0, 1fr) 120px; }
  .contract-toolbar .arco-btn { grid-column: 1 / -1; width: 100%; }
  .slot-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  :global(.player-inspector-drawer .arco-drawer-body) { padding: 10px; }
}

@media (max-width: 460px) {
  .inspector-title .arco-tag { display: none; }
  .kv-grid, .kv-grid.compact, .slot-grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
