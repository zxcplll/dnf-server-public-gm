<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import Request from '../../../api/Request';

interface GuildRow {
  guildId: number;
  guildName: string;
  masterId: number;
  masterNo: number;
  masterName: string;
  level: number;
  guildExp: number;
  fund: number;
  memberCount: number;
  createTime?: string;
  expireFlag: number;
  minLevel: number;
  maxLevel: number;
  maxFund: number;
}

interface GuildMember {
  guildId: number;
  memberId: number;
  serverId: number;
  characNo: number;
  characName: string;
  grade: number;
  gradeName: string;
  level: number;
  memberPoint: number;
  memberTime?: string;
  lastPlayTime?: string;
  leader: boolean;
  canEditGrade: boolean;
}

interface GradeOption {
  value: number;
  label: string;
}

interface AvailableCharacter {
  characNo: number;
  characName: string;
  level: number;
  memberId: number;
  accountName?: string;
  accountname?: string;
}

const filters = reactive({
  guildId: undefined as number | undefined,
  keyword: '',
  page: 1,
  pageSize: 20
});
const guildResult = ref({ page: 1, pageSize: 20, totalSize: 0, list: [] as GuildRow[] });
const loading = ref(false);
let guildRequestSequence = 0;
const managerVisible = ref(false);
const activeTab = ref('members');
const selectedGuild = ref<GuildRow | null>(null);

const memberFilters = reactive({ keyword: '', page: 1, pageSize: 20 });
const memberResult = ref({ page: 1, pageSize: 20, totalSize: 0, list: [] as GuildMember[] });
const gradeOptions = ref<GradeOption[]>([
  { value: 2, label: '副会长' },
  { value: 3, label: '优秀' },
  { value: 4, label: '普通' }
]);
const memberLoading = ref(false);
let memberRequestSequence = 0;
const gradeSaving = ref<number | null>(null);
const gradeDrafts = reactive<Record<number, number>>({});

const addMemberVisible = ref(false);
const candidateFilters = reactive({ keyword: '', page: 1, pageSize: 10 });
const candidateResult = ref({
  page: 1,
  pageSize: 10,
  totalSize: 0,
  list: [] as AvailableCharacter[]
});
const candidateLoading = ref(false);
let candidateRequestSequence = 0;
const addingCharacNo = ref<number | null>(null);

const settings = reactive({
  level: 1,
  fund: 0,
  expectedLevel: 1,
  expectedFund: 0
});
const settingsSaving = ref(false);

const numberFormatter = new Intl.NumberFormat('zh-CN');
const formatNumber = (value: number | string | null | undefined) =>
  numberFormatter.format(Number(value || 0));

const settingsChanged = computed(() =>
  settings.level !== settings.expectedLevel || settings.fund !== settings.expectedFund
);

const buildGuildUrl = () => {
  const params = new URLSearchParams({
    page: String(filters.page),
    pageSize: String(filters.pageSize)
  });
  if (filters.guildId) params.set('guildId', String(filters.guildId));
  if (filters.keyword.trim()) params.set('keyword', filters.keyword.trim());
  return `/api/v1/gm/guilds?${params.toString()}`;
};

const loadGuilds = async (resetPage = false) => {
  if (resetPage) filters.page = 1;
  const requestSequence = ++guildRequestSequence;
  loading.value = true;
  try {
    const response = await Request.get<any>(buildGuildUrl());
    if (requestSequence !== guildRequestSequence) return;
    guildResult.value = {
      page: Number(response.data?.page || filters.page),
      pageSize: Number(response.data?.pageSize || filters.pageSize),
      totalSize: Number(response.data?.totalSize || 0),
      list: response.data?.list || []
    };
    filters.page = guildResult.value.page;
  } catch (error: any) {
    if (requestSequence !== guildRequestSequence) return;
    Request.showError(error, '公会查询失败');
  } finally {
    if (requestSequence === guildRequestSequence) loading.value = false;
  }
};

const resetGuildFilters = () => {
  filters.guildId = undefined;
  filters.keyword = '';
  loadGuilds(true);
};

const syncSettings = (guild: GuildRow) => {
  settings.level = Number(guild.level || 1);
  settings.fund = Number(guild.fund || 0);
  settings.expectedLevel = settings.level;
  settings.expectedFund = settings.fund;
};

const buildMemberUrl = (guildId: number) => {
  const params = new URLSearchParams({
    page: String(memberFilters.page),
    pageSize: String(memberFilters.pageSize)
  });
  if (memberFilters.keyword.trim()) params.set('keyword', memberFilters.keyword.trim());
  return `/api/v1/gm/guilds/${guildId}/members?${params.toString()}`;
};

const clearGradeDrafts = () => {
  Object.keys(gradeDrafts).forEach((key) => delete gradeDrafts[Number(key)]);
};

const loadMembers = async (resetPage = false) => {
  const guild = selectedGuild.value;
  if (!guild) return;
  if (resetPage) memberFilters.page = 1;
  const guildId = guild.guildId;
  const requestSequence = ++memberRequestSequence;
  memberLoading.value = true;
  clearGradeDrafts();
  try {
    const response = await Request.get<any>(buildMemberUrl(guildId));
    if (
      requestSequence !== memberRequestSequence ||
      !managerVisible.value ||
      selectedGuild.value?.guildId !== guildId
    ) return;
    memberResult.value = {
      page: Number(response.data?.page || memberFilters.page),
      pageSize: Number(response.data?.pageSize || memberFilters.pageSize),
      totalSize: Number(response.data?.totalSize || 0),
      list: response.data?.list || []
    };
    memberFilters.page = memberResult.value.page;
    gradeOptions.value = response.data?.gradeOptions || gradeOptions.value;
    if (response.data?.guild) {
      selectedGuild.value = response.data.guild;
      updateGuildListRow(response.data.guild);
      if (!settingsChanged.value) syncSettings(response.data.guild);
    }
  } catch (error: any) {
    if (requestSequence !== memberRequestSequence || selectedGuild.value?.guildId !== guildId) return;
    Request.showError(error, '公会成员读取失败');
  } finally {
    if (requestSequence === memberRequestSequence) memberLoading.value = false;
  }
};

const buildCandidateUrl = () => {
  const params = new URLSearchParams({
    page: String(candidateFilters.page),
    pageSize: String(candidateFilters.pageSize)
  });
  if (candidateFilters.keyword.trim()) params.set('keyword', candidateFilters.keyword.trim());
  return `/api/v1/gm/guilds/available-characters?${params.toString()}`;
};

const loadCandidates = async (resetPage = false) => {
  if (resetPage) candidateFilters.page = 1;
  const requestSequence = ++candidateRequestSequence;
  candidateLoading.value = true;
  try {
    const response = await Request.get<any>(buildCandidateUrl());
    if (requestSequence !== candidateRequestSequence || !addMemberVisible.value) return;
    candidateResult.value = {
      page: Number(response.data?.page || candidateFilters.page),
      pageSize: Number(response.data?.pageSize || candidateFilters.pageSize),
      totalSize: Number(response.data?.totalSize || 0),
      list: response.data?.list || []
    };
    candidateFilters.page = candidateResult.value.page;
  } catch (error: any) {
    if (requestSequence !== candidateRequestSequence || !addMemberVisible.value) return;
    Request.showError(error, '可加入角色读取失败');
  } finally {
    if (requestSequence === candidateRequestSequence) candidateLoading.value = false;
  }
};

const openAddMember = () => {
  candidateFilters.keyword = '';
  candidateFilters.page = 1;
  candidateFilters.pageSize = 10;
  addMemberVisible.value = true;
  loadCandidates(true);
};

const addGuildMember = (character: AvailableCharacter) => {
  const guild = selectedGuild.value;
  if (!guild || addingCharacNo.value !== null) return;
  Modal.confirm({
    title: '添加公会成员',
    content: `确认将“${character.characName}”加入公会“${guild.guildName}”？加入后的成员职级为优秀。`,
    okText: '确认加入',
    cancelText: '取消',
    escToClose: false,
    maskClosable: false,
    onBeforeOk: async () => {
      addingCharacNo.value = character.characNo;
      try {
        const response = await Request.post<any>(`/api/v1/gm/guilds/${guild.guildId}/members`, {
          characNo: character.characNo
        });
        if (response.data?.guild) {
          selectedGuild.value = response.data.guild;
          if (!settingsChanged.value) syncSettings(response.data.guild);
          updateGuildListRow(response.data.guild);
        }
        await Promise.all([loadCandidates(), loadMembers(), loadGuilds()]);
        Message.success(`${character.characName} 已加入 ${guild.guildName}`);
        return true;
      } catch (error: any) {
        Request.showError(error, '添加公会成员失败');
        return false;
      } finally {
        addingCharacNo.value = null;
      }
    }
  });
};

const openManager = (guild: GuildRow) => {
  selectedGuild.value = { ...guild };
  activeTab.value = 'members';
  memberFilters.keyword = '';
  memberFilters.page = 1;
  memberFilters.pageSize = 20;
  syncSettings(guild);
  managerVisible.value = true;
  loadMembers(true);
};

const updateGuildListRow = (guild: GuildRow) => {
  const index = guildResult.value.list.findIndex((item) => item.guildId === guild.guildId);
  if (index >= 0) guildResult.value.list[index] = { ...guildResult.value.list[index], ...guild };
};

const saveSettings = () => {
  const guild = selectedGuild.value;
  if (!guild || !settingsChanged.value || settingsSaving.value) return;
  const levelChange = `等级 ${settings.expectedLevel} → ${settings.level}`;
  const fundChange = `资金 ${formatNumber(settings.expectedFund)} → ${formatNumber(settings.fund)}`;
  Modal.confirm({
    title: `保存 ${guild.guildName} 的公会设置`,
    content: `${levelChange}；${fundChange}`,
    okText: '确认保存',
    cancelText: '取消',
    escToClose: false,
    maskClosable: false,
    onBeforeOk: async () => {
      settingsSaving.value = true;
      try {
        const response = await Request.put<GuildRow>(`/api/v1/gm/guilds/${guild.guildId}`, {
          level: settings.level,
          fund: settings.fund,
          expectedLevel: settings.expectedLevel,
          expectedFund: settings.expectedFund
        });
        selectedGuild.value = response.data;
        syncSettings(response.data);
        updateGuildListRow(response.data);
        Message.success('公会等级与资金已更新');
        return true;
      } catch (error: any) {
        Request.showError(error, '公会设置保存失败');
        return false;
      } finally {
        settingsSaving.value = false;
      }
    }
  });
};

const draftGrade = (member: GuildMember) => gradeDrafts[member.characNo] ?? member.grade;

const saveMemberGrade = (member: GuildMember) => {
  if (!member.canEditGrade || gradeSaving.value !== null) return;
  const grade = draftGrade(member);
  if (grade === member.grade) return;
  const option = gradeOptions.value.find((item) => item.value === grade);
  Modal.confirm({
    title: '修改成员职级',
    content: `将“${member.characName}”从${member.gradeName}调整为${option?.label || grade}。`,
    okText: '确认修改',
    cancelText: '取消',
    escToClose: false,
    maskClosable: false,
    onBeforeOk: async () => {
      gradeSaving.value = member.characNo;
      try {
        const response = await Request.put<GuildMember>(
          `/api/v1/gm/guilds/${member.guildId}/members/${member.characNo}/grade`,
          { grade, expectedGrade: member.grade }
        );
        const index = memberResult.value.list.findIndex((item) => item.characNo === member.characNo);
        if (index >= 0) memberResult.value.list[index] = response.data;
        delete gradeDrafts[member.characNo];
        Message.success(`${member.characName} 的职级已更新`);
        return true;
      } catch (error: any) {
        Request.showError(error, '成员职级保存失败');
        return false;
      } finally {
        gradeSaving.value = null;
      }
    }
  });
};

const gradeColor = (grade: number) => {
  if (grade === 1) return 'orange';
  if (grade === 2) return 'purple';
  if (grade === 3) return 'cyan';
  return 'gray';
};

const onGuildPageChange = (page: number) => {
  filters.page = page;
  loadGuilds();
};

const onGuildPageSizeChange = (pageSize: number) => {
  filters.pageSize = pageSize;
  loadGuilds(true);
};

const onMemberPageChange = (page: number) => {
  memberFilters.page = page;
  loadMembers();
};

const onMemberPageSizeChange = (pageSize: number) => {
  memberFilters.pageSize = pageSize;
  loadMembers(true);
};

const onCandidatePageChange = (page: number) => {
  candidateFilters.page = page;
  loadCandidates();
};

const onCandidatePageSizeChange = (pageSize: number) => {
  candidateFilters.pageSize = pageSize;
  loadCandidates(true);
};

onMounted(() => loadGuilds());
</script>

<template>
  <div class="guild-page">
    <a-card class="filter-card">
      <template #title>公会管理</template>
      <a-form class="gm-filter-form" layout="inline" :model="filters" @submit-success="loadGuilds(true)">
        <a-form-item label="公会 ID">
          <a-input-number v-model="filters.guildId" :min="1" placeholder="输入 ID" allow-clear />
        </a-form-item>
        <a-form-item label="公会或会长">
          <a-input v-model="filters.keyword" placeholder="输入公会名或会长名" allow-clear @press-enter="loadGuilds(true)" />
        </a-form-item>
        <a-form-item class="gm-filter-actions">
          <a-space>
            <a-button type="primary" :loading="loading" @click="loadGuilds(true)">
              <template #icon><icon-search /></template>
              查询
            </a-button>
            <a-button :disabled="loading" @click="resetGuildFilters">重置</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>

    <a-card class="guild-table-card">
      <a-table
        :data="guildResult.list"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1120 }"
        row-key="guildId"
      >
        <template #empty><a-empty description="暂无公会" /></template>
        <template #columns>
          <a-table-column title="公会 ID" data-index="guildId" :width="88" />
          <a-table-column title="公会" :width="190">
            <template #cell="{ record }">
              <div class="guild-name-cell">
                <span class="guild-emblem"><icon-user-group /></span>
                <div>
                  <strong>{{ record.guildName || '--' }}</strong>
                  <small>创建于 {{ record.createTime || '--' }}</small>
                </div>
              </div>
            </template>
          </a-table-column>
          <a-table-column title="会长" :width="160">
            <template #cell="{ record }">
              <strong>{{ record.masterName || '--' }}</strong>
              <div class="sub-value">角色 ID {{ record.masterNo }}</div>
            </template>
          </a-table-column>
          <a-table-column title="等级" :width="92">
            <template #cell="{ record }"><span class="level-badge">LV.{{ record.level }}</span></template>
          </a-table-column>
          <a-table-column title="成员" :width="92">
            <template #cell="{ record }">{{ record.memberCount }} 人</template>
          </a-table-column>
          <a-table-column title="公会资金" :width="160">
            <template #cell="{ record }"><span class="fund-value">{{ formatNumber(record.fund) }}</span></template>
          </a-table-column>
          <a-table-column title="公会经验" :width="130">
            <template #cell="{ record }">{{ formatNumber(record.guildExp) }}</template>
          </a-table-column>
          <a-table-column title="状态" :width="96">
            <template #cell="{ record }">
              <a-tag :color="record.expireFlag ? 'red' : 'green'">{{ record.expireFlag ? '已过期' : '正常' }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="112" fixed="right">
            <template #cell="{ record }">
              <a-button type="primary" size="small" :aria-label="`管理公会 ${record.guildName}`" @click="openManager(record)">
                <template #icon><icon-settings /></template>
                管理
              </a-button>
            </template>
          </a-table-column>
        </template>
      </a-table>
      <div class="gm-pagination-row">
        <a-pagination
          :current="filters.page"
          :page-size="filters.pageSize"
          :total="guildResult.totalSize"
          show-total
          show-jumper
          show-page-size
          :page-size-options="[10, 20, 50, 100]"
          @change="onGuildPageChange"
          @page-size-change="onGuildPageSizeChange"
        />
      </div>
    </a-card>

    <a-modal
      v-model:visible="managerVisible"
      :width="'min(1240px, calc(100vw - 24px))'"
      :footer="false"
      :mask-closable="gradeSaving === null && !settingsSaving && addingCharacNo === null"
      :closable="gradeSaving === null && !settingsSaving && addingCharacNo === null"
      :esc-to-close="gradeSaving === null && !settingsSaving && addingCharacNo === null"
      :unmount-on-close="true"
      :modal-class="['guild-theme-modal', 'guild-manager-modal']"
      title="公会控制台"
    >
      <template v-if="selectedGuild">
        <div class="guild-summary">
          <div class="summary-identity">
            <span class="summary-icon"><icon-user-group /></span>
            <div>
              <strong>{{ selectedGuild.guildName }}</strong>
              <span>公会 ID {{ selectedGuild.guildId }} · 会长 {{ selectedGuild.masterName }}</span>
            </div>
          </div>
          <div class="summary-metrics">
            <div><span>等级</span><strong>LV.{{ selectedGuild.level }}</strong></div>
            <div><span>成员</span><strong>{{ selectedGuild.memberCount }}</strong></div>
            <div><span>资金</span><strong>{{ formatNumber(selectedGuild.fund) }}</strong></div>
          </div>
        </div>

        <a-tabs v-model:active-key="activeTab" class="guild-tabs">
          <a-tab-pane key="members" title="成员管理">
            <div class="member-toolbar">
              <a-input
                v-model="memberFilters.keyword"
                allow-clear
                placeholder="搜索角色名、角色 ID 或 UID"
                @press-enter="loadMembers(true)"
              >
                <template #prefix><icon-search /></template>
              </a-input>
              <div class="member-toolbar-actions">
                <a-button type="primary" :disabled="memberLoading" @click="openAddMember">
                  <template #icon><icon-user-plus /></template>
                  添加成员
                </a-button>
                <a-button :loading="memberLoading" @click="loadMembers(true)">
                  <template #icon><icon-refresh /></template>
                  刷新
                </a-button>
              </div>
            </div>

            <a-table
              :data="memberResult.list"
              :loading="memberLoading"
              :pagination="false"
              :scroll="{ x: 1092 }"
              row-key="characNo"
            >
              <template #empty><a-empty description="暂无公会成员" /></template>
              <template #columns>
                <a-table-column title="角色" :width="210">
                  <template #cell="{ record }">
                    <div class="member-cell">
                      <span class="level-badge">LV.{{ record.level || 1 }}</span>
                      <div><strong class="member-name">{{ record.characName }}</strong><small>角色 ID {{ record.characNo }}</small></div>
                    </div>
                  </template>
                </a-table-column>
                <a-table-column title="账号 UID" :width="140">
                  <template #cell="{ record }"><span class="member-uid">{{ record.memberId }}</span></template>
                </a-table-column>
                <a-table-column title="成员职级" :width="220">
                  <template #cell="{ record }">
                    <div v-if="record.leader" class="leader-lock">
                      <a-tag :color="gradeColor(1)">会长</a-tag>
                      <span><icon-lock /> 不可更改</span>
                    </div>
                    <a-select
                      v-else
                      :model-value="draftGrade(record)"
                      :disabled="gradeSaving !== null"
                      :trigger-props="{ contentClass: 'guild-grade-select-popup' }"
                      @change="(value) => gradeDrafts[record.characNo] = Number(value)"
                    >
                      <a-option v-for="option in gradeOptions" :key="option.value" :value="option.value">
                        {{ option.value }} · {{ option.label }}
                      </a-option>
                    </a-select>
                  </template>
                </a-table-column>
                <a-table-column title="成员贡献" :width="110">
                  <template #cell="{ record }"><span class="member-point">{{ formatNumber(record.memberPoint) }}</span></template>
                </a-table-column>
                <a-table-column title="加入时间" :width="165">
                  <template #cell="{ record }"><span class="member-time">{{ record.memberTime || '--' }}</span></template>
                </a-table-column>
                <a-table-column title="最后游戏" :width="165">
                  <template #cell="{ record }"><span class="member-time">{{ record.lastPlayTime || '--' }}</span></template>
                </a-table-column>
                <a-table-column title="操作" :width="82" fixed="right">
                  <template #cell="{ record }">
                    <a-tooltip v-if="record.canEditGrade" content="保存成员职级">
                      <a-button
                        type="primary"
                        shape="circle"
                        :aria-label="`保存 ${record.characName} 的成员职级`"
                        :loading="gradeSaving === record.characNo"
                        :disabled="gradeSaving !== null || draftGrade(record) === record.grade"
                        @click="saveMemberGrade(record)"
                      ><icon-save /></a-button>
                    </a-tooltip>
                    <span v-else class="locked-operation">--</span>
                  </template>
                </a-table-column>
              </template>
            </a-table>

            <div class="gm-pagination-row">
              <a-pagination
                :current="memberFilters.page"
                :page-size="memberFilters.pageSize"
                :total="memberResult.totalSize"
                show-total
                show-jumper
                show-page-size
                :page-size-options="[10, 20, 50, 100]"
                @change="onMemberPageChange"
                @page-size-change="onMemberPageSizeChange"
              />
            </div>
          </a-tab-pane>

          <a-tab-pane key="settings" title="公会设置">
            <div class="settings-intro">
              <strong>核心数值</strong>
              <span>保存时会校验打开页面时的旧值，检测到其他管理员已修改会要求刷新。</span>
            </div>
            <a-form class="settings-form" layout="vertical" :model="settings">
              <div class="settings-grid">
                <a-form-item label="公会等级">
                  <a-input-number
                    v-model="settings.level"
                    :min="selectedGuild.minLevel || 1"
                    :max="selectedGuild.maxLevel || 30"
                    :precision="0"
                  >
                    <template #prefix>LV.</template>
                  </a-input-number>
                  <template #extra>允许范围 1–{{ selectedGuild.maxLevel || 30 }}</template>
                </a-form-item>
                <a-form-item label="公会资金">
                  <a-input-number
                    v-model="settings.fund"
                    :min="0"
                    :max="selectedGuild.maxFund || 4294967295"
                    :precision="0"
                    hide-button
                  />
                  <template #extra>当前 {{ formatNumber(settings.expectedFund) }}，最大 4,294,967,295</template>
                </a-form-item>
              </div>
              <div class="settings-actions">
                <a-button
                  type="primary"
                  :loading="settingsSaving"
                  :disabled="!settingsChanged"
                  @click="saveSettings"
                >
                  <template #icon><icon-save /></template>
                  保存公会设置
                </a-button>
              </div>
            </a-form>
          </a-tab-pane>
        </a-tabs>
      </template>
    </a-modal>

    <a-modal
      v-model:visible="addMemberVisible"
      :width="'min(820px, calc(100vw - 24px))'"
      :footer="false"
      :mask-closable="addingCharacNo === null"
      :closable="addingCharacNo === null"
      :esc-to-close="addingCharacNo === null"
      :unmount-on-close="true"
      :modal-class="['guild-theme-modal', 'guild-member-add-modal']"
      title="添加公会成员"
    >
      <template v-if="selectedGuild">
        <div class="candidate-heading">
          <div>
            <span>目标公会</span>
            <strong>{{ selectedGuild.guildName }}</strong>
          </div>
          <a-tag color="cyan">仅显示无公会角色</a-tag>
        </div>

        <div class="candidate-toolbar">
          <a-input
            v-model="candidateFilters.keyword"
            allow-clear
            placeholder="搜索角色名、角色 ID、账号或 UID"
            @press-enter="loadCandidates(true)"
          >
            <template #prefix><icon-search /></template>
          </a-input>
          <a-button type="primary" :loading="candidateLoading" @click="loadCandidates(true)">
            <template #icon><icon-search /></template>
            查询
          </a-button>
        </div>

        <a-table
          :data="candidateResult.list"
          :loading="candidateLoading"
          :pagination="false"
          :scroll="{ x: 690 }"
          row-key="characNo"
        >
          <template #empty><a-empty description="暂无可加入的角色" /></template>
          <template #columns>
            <a-table-column title="角色" :width="240">
              <template #cell="{ record }">
                <div class="member-cell">
                  <span class="level-badge">LV.{{ record.level || 1 }}</span>
                  <div>
                    <strong class="member-name">{{ record.characName }}</strong>
                    <small>角色 ID {{ record.characNo }}</small>
                  </div>
                </div>
              </template>
            </a-table-column>
            <a-table-column title="账号" :width="180">
              <template #cell="{ record }">{{ record.accountName || record.accountname || '--' }}</template>
            </a-table-column>
            <a-table-column title="UID" :width="130">
              <template #cell="{ record }"><span class="member-uid">{{ record.memberId }}</span></template>
            </a-table-column>
            <a-table-column title="操作" :width="110" fixed="right">
              <template #cell="{ record }">
                <a-button
                  type="primary"
                  size="small"
                  :loading="addingCharacNo === record.characNo"
                  :disabled="addingCharacNo !== null"
                  @click="addGuildMember(record)"
                >
                  <template #icon><icon-user-plus /></template>
                  加入
                </a-button>
              </template>
            </a-table-column>
          </template>
        </a-table>

        <div class="gm-pagination-row">
          <a-pagination
            :current="candidateFilters.page"
            :page-size="candidateFilters.pageSize"
            :total="candidateResult.totalSize"
            show-total
            show-jumper
            show-page-size
            :page-size-options="[10, 20, 50]"
            @change="onCandidatePageChange"
            @page-size-change="onCandidatePageSizeChange"
          />
        </div>
      </template>
    </a-modal>
  </div>
</template>

<style scoped lang="less">
.guild-page {
  padding: 16px;
}

.guild-table-card {
  margin-top: 12px;
}

.guild-name-cell,
.member-cell,
.summary-identity,
.leader-lock {
  display: flex;
  align-items: center;
  min-width: 0;
}

.guild-name-cell,
.member-cell {
  gap: 10px;
}

.guild-name-cell > div,
.member-cell > div,
.summary-identity > div {
  min-width: 0;
}

.guild-name-cell strong,
.guild-name-cell small,
.member-cell strong,
.member-cell small,
.summary-identity strong,
.summary-identity span {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.guild-name-cell small,
.member-cell small,
.summary-identity span,
.sub-value {
  margin-top: 4px;
  color: var(--gm-muted);
  font-size: 12px;
}

.member-name {
  color: #f4f8ff;
  font-size: 15px;
  font-weight: 700;
  line-height: 1.35;
}

.member-uid {
  color: #8db4ff;
  font-family: Consolas, "SFMono-Regular", monospace;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}

.member-point {
  color: var(--gm-amber);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}

.member-time {
  color: #a8b9ce;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.guild-emblem,
.summary-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  color: var(--gm-cyan);
  border: 1px solid rgba(77, 228, 210, 0.5);
  background: var(--gm-cyan-soft);
}

.guild-emblem {
  width: 34px;
  height: 34px;
  border-radius: 6px;
}

.summary-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  font-size: 22px;
}

.level-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  min-width: 54px;
  height: 30px;
  padding: 0 8px;
  border: 1px solid rgba(77, 228, 210, 0.65);
  border-radius: 6px;
  color: var(--gm-cyan);
  background: var(--gm-cyan-soft);
  font-size: 12px;
  font-weight: 700;
}

.fund-value {
  color: var(--gm-amber);
  font-variant-numeric: tabular-nums;
}

.guild-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 4px 0 18px;
  border-bottom: 1px solid var(--gm-rule);
}

.summary-identity {
  flex: 1 1 auto;
  gap: 12px;
}

.summary-identity strong {
  color: var(--gm-text);
  font-size: 20px;
}

.summary-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(92px, auto));
  gap: 8px;
}

.summary-metrics > div {
  min-width: 92px;
  padding: 8px 12px;
  border-left: 1px solid var(--gm-rule);
}

.summary-metrics span,
.summary-metrics strong {
  display: block;
}

.summary-metrics span {
  color: var(--gm-muted);
  font-size: 12px;
}

.summary-metrics strong {
  margin-top: 4px;
  color: var(--gm-text);
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}

.guild-tabs {
  margin-top: 4px;
}

.member-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.member-toolbar .arco-input-wrapper {
  width: min(420px, 100%);
}

.member-toolbar-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
}

.candidate-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--gm-rule);
}

.candidate-heading > div,
.candidate-heading span,
.candidate-heading strong {
  display: block;
  min-width: 0;
}

.candidate-heading span {
  color: var(--gm-muted);
  font-size: 12px;
}

.candidate-heading strong {
  margin-top: 4px;
  overflow: hidden;
  color: var(--gm-text);
  font-size: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.candidate-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.candidate-toolbar .arco-input-wrapper {
  width: min(480px, 100%);
}

.leader-lock {
  gap: 8px;
}

.leader-lock span {
  color: var(--gm-muted);
  font-size: 12px;
}

.locked-operation {
  color: var(--gm-subtle);
}

.settings-intro {
  padding: 4px 0 18px;
}

.settings-intro strong,
.settings-intro span {
  display: block;
}

.settings-intro strong {
  color: var(--gm-text);
  font-size: 16px;
}

.settings-intro span {
  margin-top: 6px;
  color: var(--gm-muted);
}

.settings-form {
  box-shadow: none !important;
}

.settings-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.settings-grid :deep(.arco-input-number) {
  width: 100%;
}

.settings-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid var(--gm-rule);
}

:global(.guild-theme-modal.arco-modal) {
  --guild-modal-surface: #101a2a;
  --guild-modal-raised: #16243a;
  --guild-modal-hover: #1b2d46;
  overflow: hidden;
  border: 1px solid rgba(109, 157, 255, 0.26);
  border-radius: 8px;
  background: var(--guild-modal-surface);
  box-shadow: 0 26px 80px rgba(0, 0, 0, 0.5);
  color: var(--gm-text);
}

:global(.guild-theme-modal .arco-modal-header) {
  border-bottom: 1px solid rgba(151, 174, 204, 0.18);
  background: #0d1726;
}

:global(.guild-theme-modal .arco-modal-title) {
  color: #f4f8ff;
  font-size: 16px;
  font-weight: 700;
}

:global(.guild-theme-modal .arco-modal-close-btn) {
  color: #9db0c8;
}

:global(.guild-theme-modal .arco-modal-close-btn:hover) {
  color: var(--gm-cyan);
  background: rgba(77, 228, 210, 0.1);
}

:global(.guild-theme-modal .arco-modal-body) {
  background: var(--guild-modal-surface);
  color: var(--gm-text);
}

:global(.guild-theme-modal .arco-tabs-nav::before) {
  background: rgba(151, 174, 204, 0.2);
}

:global(.guild-theme-modal .arco-tabs-tab) {
  color: #9fb1c8;
}

:global(.guild-theme-modal .arco-tabs-tab-active),
:global(.guild-theme-modal .arco-tabs-tab:hover) {
  color: var(--gm-cyan);
}

:global(.guild-theme-modal .arco-table-container) {
  overflow: hidden;
  border: 1px solid rgba(151, 174, 204, 0.2);
  border-radius: 6px;
  background: var(--guild-modal-surface);
}

:global(.guild-theme-modal .arco-table-th) {
  color: #aebed2 !important;
  background: var(--guild-modal-raised) !important;
  border-color: rgba(151, 174, 204, 0.24) !important;
  font-size: 12px;
  font-weight: 700;
}

:global(.guild-theme-modal .arco-table-td) {
  color: #dce8f6 !important;
  background: var(--guild-modal-surface) !important;
  border-color: rgba(151, 174, 204, 0.17) !important;
}

:global(.guild-theme-modal .arco-table-border .arco-table-th),
:global(.guild-theme-modal .arco-table-border .arco-table-td) {
  border-right-color: rgba(151, 174, 204, 0.12) !important;
}

:global(.guild-theme-modal .arco-table-tr:hover .arco-table-td) {
  background: var(--guild-modal-hover) !important;
}

:global(.guild-theme-modal .arco-table-cell) {
  min-height: 38px;
}

:global(.guild-theme-modal .arco-table-col-fixed-right-first::after) {
  box-shadow: -10px 0 22px rgba(3, 9, 17, 0.3);
}

:global(.guild-theme-modal .arco-input-wrapper),
:global(.guild-theme-modal .arco-select-view),
:global(.guild-theme-modal .arco-input-number) {
  border-color: rgba(151, 174, 204, 0.25);
  background: var(--guild-modal-raised);
  color: #edf5ff;
}

:global(.guild-theme-modal .arco-input),
:global(.guild-theme-modal .arco-select-view-value),
:global(.guild-theme-modal .arco-select-view-suffix),
:global(.guild-theme-modal .arco-input-number-input) {
  color: #edf5ff;
}

:global(.guild-theme-modal .arco-input::placeholder),
:global(.guild-theme-modal .arco-input-number-input::placeholder) {
  color: #7f93ad;
}

:global(.guild-theme-modal .arco-select-view:hover),
:global(.guild-theme-modal .arco-input-wrapper:hover),
:global(.guild-theme-modal .arco-input-number:hover) {
  border-color: rgba(77, 228, 210, 0.65);
}

:global(.guild-theme-modal .arco-pagination-total),
:global(.guild-theme-modal .arco-pagination-jumper-prepend) {
  color: #aebed2;
}

:global(.guild-theme-modal .arco-pagination-item),
:global(.guild-theme-modal .arco-pagination-prev),
:global(.guild-theme-modal .arco-pagination-next) {
  border-color: rgba(151, 174, 204, 0.22);
  background: var(--guild-modal-raised);
  color: #dce8f6;
}

:global(.guild-theme-modal .arco-pagination-item-active) {
  border-color: rgba(77, 228, 210, 0.65);
  background: rgba(77, 228, 210, 0.12);
  color: var(--gm-cyan);
}

:global(.guild-theme-modal .arco-empty-description) {
  color: #8fa4bf;
}

:global(.guild-theme-modal .leader-lock .arco-tag) {
  border-color: rgba(255, 188, 104, 0.42);
  background: rgba(255, 188, 104, 0.13);
  color: #ffd09a;
}

:global(.guild-grade-select-popup .arco-select-dropdown) {
  overflow: hidden;
  border: 1px solid rgba(109, 157, 255, 0.3);
  border-radius: 6px;
  background: #101a2a;
  box-shadow: 0 14px 36px rgba(0, 0, 0, 0.42);
}

:global(.guild-grade-select-popup .arco-select-option) {
  background: transparent !important;
  color: #dce8f6 !important;
}

:global(.guild-grade-select-popup .arco-select-option:hover),
:global(.guild-grade-select-popup .arco-select-option-active) {
  background: #1b2d46 !important;
  color: #f4f8ff !important;
}

:global(.guild-grade-select-popup .arco-select-option-selected) {
  background: rgba(77, 228, 210, 0.12) !important;
  color: #4de4d2 !important;
  font-weight: 700;
}

@media (max-width: 900px) {
  .guild-page {
    padding: 8px;
  }

  .guild-summary {
    align-items: flex-start;
    flex-direction: column;
  }

  .summary-metrics {
    width: 100%;
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .summary-metrics,
  .settings-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .summary-metrics > div {
    border-left: 0;
    border-top: 1px solid var(--gm-rule);
  }

  .member-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .member-toolbar .arco-input-wrapper,
  .settings-actions .arco-btn {
    width: 100%;
  }

  .member-toolbar-actions,
  .candidate-toolbar {
    align-items: stretch;
    flex-direction: column;
    width: 100%;
  }

  .member-toolbar-actions .arco-btn,
  .candidate-toolbar .arco-input-wrapper,
  .candidate-toolbar .arco-btn {
    width: 100%;
  }

  .candidate-heading {
    align-items: flex-start;
  }
}
</style>
