<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import Request from '../../../api/Request';
import { gmOperations } from '../../../api/gmOperations';
import { openPlayerInspector } from '../../../composables/usePlayerInspector';

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
  guildPoint?: number;
  skillPoint?: number;
  announcement?: string;
  maxGuildExp?: number;
  maxGuildPoint?: number;
  maxSkillPoint?: number;
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
  guildExp: 0,
  guildPoint: 0,
  expectedLevel: 1,
  expectedFund: 0,
  expectedGuildExp: 0,
  expectedGuildPoint: 0,
});
const settingsSaving = ref(false);
const applications = ref({ page: 1, pageSize: 20, totalSize: 0, list: [] as any[] });
const applicationsLoading = ref(false);
const applicationSaving = ref<number | null>(null);
const skills = ref({ skillPoint: 0, list: [] as any[] });
const skillsLoading = ref(false);
const skillPointDelta = ref(0);
const skillSaving = ref(false);
const announcement = reactive({ content: '', expectedContent: '' });
const announcementLoading = ref(false);
const announcementSaving = ref(false);
const contributions = ref({ page: 1, pageSize: 20, totalSize: 0, list: [] as any[] });
const contributionLoading = ref(false);
const contributionSaving = ref<number | null>(null);
const contributionDrafts = reactive<Record<number, number>>({});

const numberFormatter = new Intl.NumberFormat('zh-CN');
const formatNumber = (value: number | string | null | undefined) =>
  numberFormatter.format(Number(value || 0));

const settingsChanged = computed(() =>
  settings.level !== settings.expectedLevel || settings.fund !== settings.expectedFund ||
  settings.guildExp !== settings.expectedGuildExp || settings.guildPoint !== settings.expectedGuildPoint
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
  settings.guildExp = Number(guild.guildExp || 0);
  settings.guildPoint = Number(guild.guildPoint || 0);
  settings.expectedLevel = settings.level;
  settings.expectedFund = settings.fund;
  settings.expectedGuildExp = settings.guildExp;
  settings.expectedGuildPoint = settings.guildPoint;
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
        const requestId = operationRequestId();
        const response = await Request.post<any>(`/api/v1/gm/guilds/${guild.guildId}/members`, {
          characNo: character.characNo,
          requestId,
        }, { headers: { requestId } });
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

const operationRequestId = () => globalThis.crypto?.randomUUID?.() || `guild-${Date.now()}-${Math.random().toString(16).slice(2)}`;

const loadApplications = async () => {
  if (!selectedGuild.value || applicationsLoading.value) return;
  applicationsLoading.value = true;
  try {
    const response = await gmOperations.getGuildApplications(selectedGuild.value.guildId);
    applications.value = { page: Number(response?.page || 1), pageSize: Number(response?.pageSize || 20), totalSize: Number(response?.totalSize || 0), list: response?.list || [] };
  } catch (error: any) {
    Request.showError(error, '入会申请读取失败');
  } finally {
    applicationsLoading.value = false;
  }
};

const changeApplication = (row: any, action: 'approve' | 'reject') => {
  const guild = selectedGuild.value;
  if (!guild || applicationSaving.value !== null) return;
  const characNo = Number(row.characNo);
  Modal.confirm({
    title: action === 'approve' ? '批准入会申请' : '拒绝入会申请',
    content: `${action === 'approve' ? '批准' : '拒绝'}“${row.characName || row.name || row.characNo}”加入 ${guild.guildName}？`,
    okText: action === 'approve' ? '批准' : '拒绝', cancelText: '取消', maskClosable: false, escToClose: false,
    onBeforeOk: async () => {
      applicationSaving.value = characNo;
      try {
        await gmOperations.changeGuildApplication(guild.guildId, characNo, action, { requestId: operationRequestId(), expectedStatus: row.status });
        await Promise.all([loadApplications(), loadMembers(), loadGuilds()]);
        Message.success(action === 'approve' ? '申请已批准' : '申请已拒绝');
        return true;
      } catch (error: any) {
        Request.showError(error, '入会申请处理失败');
        return false;
      } finally {
        applicationSaving.value = null;
      }
    },
  });
};

const loadSkills = async () => {
  if (!selectedGuild.value || skillsLoading.value) return;
  skillsLoading.value = true;
  try {
    const response = await gmOperations.getGuildSkills(selectedGuild.value.guildId);
    skills.value = { skillPoint: Number(response?.skillPoint ?? selectedGuild.value.skillPoint ?? 0), list: response?.list || [] };
    skillPointDelta.value = 0;
  } catch (error: any) {
    Request.showError(error, '公会技能读取失败');
  } finally {
    skillsLoading.value = false;
  }
};

const saveSkillPoints = () => {
  const guild = selectedGuild.value;
  const delta = Number(skillPointDelta.value || 0);
  if (!guild || !delta || skillSaving.value) return;
  Modal.confirm({
    title: '调整公会技能点',
    content: `${guild.guildName}：${skills.value.skillPoint} ${delta > 0 ? '+' : '-'} ${Math.abs(delta)}`,
    okText: '确认调整', cancelText: '取消', maskClosable: false, escToClose: false,
    onBeforeOk: async () => {
      skillSaving.value = true;
      try {
        await gmOperations.changeGuildSkillPoints(guild.guildId, { delta, expectedSkillPoint: skills.value.skillPoint, requestId: operationRequestId() });
        await Promise.all([loadSkills(), loadGuilds()]);
        Message.success('公会技能点已更新');
        return true;
      } catch (error: any) {
        Request.showError(error, '技能点调整失败');
        return false;
      } finally {
        skillSaving.value = false;
      }
    },
  });
};

const loadAnnouncement = async () => {
  if (!selectedGuild.value || announcementLoading.value) return;
  announcementLoading.value = true;
  try {
    const response = await gmOperations.getGuildAnnouncement(selectedGuild.value.guildId);
    announcement.content = String(response?.notice ?? response?.content ?? response?.announcement ?? selectedGuild.value.announcement ?? '');
    announcement.expectedContent = announcement.content;
  } catch (error: any) {
    Request.showError(error, '公会公告读取失败');
  } finally {
    announcementLoading.value = false;
  }
};

const saveAnnouncement = () => {
  const guild = selectedGuild.value;
  if (!guild || announcement.content === announcement.expectedContent || announcementSaving.value) return;
  Modal.confirm({
    title: '更新公会公告', content: `确认保存 ${guild.guildName} 的新公告？`, okText: '保存公告', cancelText: '取消', maskClosable: false, escToClose: false,
    onBeforeOk: async () => {
      announcementSaving.value = true;
      try {
        await gmOperations.updateGuildAnnouncement(guild.guildId, { content: announcement.content, expectedContent: announcement.expectedContent, requestId: operationRequestId() });
        announcement.expectedContent = announcement.content;
        Message.success('公会公告已更新');
        return true;
      } catch (error: any) {
        Request.showError(error, '公会公告保存失败');
        return false;
      } finally {
        announcementSaving.value = false;
      }
    },
  });
};

const loadContributions = async () => {
  if (!selectedGuild.value || contributionLoading.value) return;
  contributionLoading.value = true;
  try {
    const response = await gmOperations.getGuildContributions(selectedGuild.value.guildId, { page: contributions.value.page, pageSize: contributions.value.pageSize });
    contributions.value = { page: Number(response?.page || 1), pageSize: Number(response?.pageSize || 20), totalSize: Number(response?.totalSize || 0), list: response?.list || [] };
    Object.keys(contributionDrafts).forEach((key) => delete contributionDrafts[Number(key)]);
  } catch (error: any) {
    Request.showError(error, '贡献记录读取失败');
  } finally {
    contributionLoading.value = false;
  }
};

const saveContribution = (row: any) => {
  const guild = selectedGuild.value;
  const characNo = Number(row.characNo);
  const current = Number(row.memberPoint ?? row.contribution ?? 0);
  const next = Number(contributionDrafts[characNo] ?? current);
  if (!guild || next === current || contributionSaving.value !== null) return;
  Modal.confirm({
    title: '修改成员贡献', content: `${row.characName || characNo}：${formatNumber(current)} → ${formatNumber(next)}`, okText: '确认修改', cancelText: '取消', maskClosable: false, escToClose: false,
    onBeforeOk: async () => {
      contributionSaving.value = characNo;
      try {
        await gmOperations.changeGuildContribution(guild.guildId, characNo, { contribution: next, expectedContribution: current, requestId: operationRequestId() });
        await Promise.all([loadContributions(), loadMembers()]);
        Message.success('成员贡献已更新');
        return true;
      } catch (error: any) {
        Request.showError(error, '成员贡献修改失败');
        return false;
      } finally {
        contributionSaving.value = null;
      }
    },
  });
};

const removeMember = (member: GuildMember) => {
  const guild = selectedGuild.value;
  if (!guild || member.leader) return;
  Modal.confirm({
    title: '移除公会成员', content: `确认将“${member.characName}”移出 ${guild.guildName}？`, okText: '确认移除', cancelText: '取消', okButtonProps: { status: 'danger' }, maskClosable: false, escToClose: false,
    onBeforeOk: async () => {
      try {
        await gmOperations.removeGuildMember(guild.guildId, member.characNo, { expectedGrade: member.grade, requestId: operationRequestId() });
        await Promise.all([loadMembers(), loadGuilds()]);
        Message.success(`${member.characName} 已移出公会`);
        return true;
      } catch (error: any) {
        Request.showError(error, '移除成员失败');
        return false;
      }
    },
  });
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
        const requestId = operationRequestId();
        const response = await Request.put<GuildRow>(`/api/v1/gm/guilds/${guild.guildId}`, {
          level: settings.level,
          fund: settings.fund,
          guildExp: settings.guildExp,
          guildPoint: settings.guildPoint,
          expectedLevel: settings.expectedLevel,
          expectedFund: settings.expectedFund,
          expectedGuildExp: settings.expectedGuildExp,
          expectedGuildPoint: settings.expectedGuildPoint,
          requestId,
        }, { headers: { requestId } });
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
        const requestId = operationRequestId();
        const response = await Request.put<GuildMember>(
          `/api/v1/gm/guilds/${member.guildId}/members/${member.characNo}/grade`,
          { grade, expectedGrade: member.grade, requestId },
          { headers: { requestId } },
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

watch(activeTab, (tab) => {
  if (!managerVisible.value || !selectedGuild.value) return;
  if (tab === 'members') loadMembers();
  if (tab === 'applications') loadApplications();
  if (tab === 'skills') loadSkills();
  if (tab === 'announcement') loadAnnouncement();
  if (tab === 'contributions') loadContributions();
});

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
                    <button class="member-cell member-profile-link" type="button" @click="openPlayerInspector(record.characNo, 'guild', '公会成员')">
                      <span class="level-badge">LV.{{ record.level || 1 }}</span>
                      <div><strong class="member-name">{{ record.characName }}</strong><small>角色 ID {{ record.characNo }}</small></div>
                    </button>
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
                <a-table-column title="操作" :width="154" fixed="right">
                  <template #cell="{ record }">
                    <a-space size="mini">
                      <a-tooltip content="查看玩家档案"><a-button shape="circle" @click="openPlayerInspector(record.characNo, 'guild', '公会成员')"><icon-eye /></a-button></a-tooltip>
                      <a-tooltip v-if="record.canEditGrade" content="保存成员职级"><a-button type="primary" shape="circle" :aria-label="`保存 ${record.characName} 的成员职级`" :loading="gradeSaving === record.characNo" :disabled="gradeSaving !== null || draftGrade(record) === record.grade" @click="saveMemberGrade(record)"><icon-save /></a-button></a-tooltip>
                      <a-tooltip v-if="!record.leader" content="移除成员"><a-button shape="circle" status="danger" @click="removeMember(record)"><icon-delete /></a-button></a-tooltip>
                      <span v-else class="locked-operation"><icon-lock /></span>
                    </a-space>
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
                <a-form-item label="公会经验">
                  <a-input-number v-model="settings.guildExp" :min="0" :max="selectedGuild.maxGuildExp || 4294967295" :precision="0" hide-button />
                  <template #extra>当前 {{ formatNumber(settings.expectedGuildExp) }}</template>
                </a-form-item>
                <a-form-item label="公会点数">
                  <a-input-number v-model="settings.guildPoint" :min="0" :max="selectedGuild.maxGuildPoint || 4294967295" :precision="0" hide-button />
                  <template #extra>当前 {{ formatNumber(settings.expectedGuildPoint) }}</template>
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

          <a-tab-pane key="applications" title="入会申请">
            <div class="member-toolbar"><div class="tab-copy"><strong>待处理申请</strong><span>批准后默认成员职级为优秀，重复申请会由服务端校验。</span></div><a-button :loading="applicationsLoading" @click="loadApplications"><template #icon><icon-refresh /></template>刷新</a-button></div>
            <a-table :data="applications.list" :loading="applicationsLoading" :pagination="false" :scroll="{ x: 820 }">
              <template #empty><a-empty description="暂无待处理入会申请" /></template>
              <template #columns>
                <a-table-column title="申请角色" :width="220"><template #cell="{ record }"><button class="member-profile-link simple" type="button" @click="openPlayerInspector(Number(record.characNo), 'profile', '公会申请')"><strong>{{ record.characName || record.name || '--' }}</strong><small>#{{ record.characNo || '--' }}</small></button></template></a-table-column>
                <a-table-column title="账号 UID" :width="150"><template #cell="{ record }"><span class="member-uid">{{ record.uid || record.memberId || '--' }}</span></template></a-table-column>
                <a-table-column title="申请时间" :width="190"><template #cell="{ record }">{{ record.applyTime || record.createdAt || '--' }}</template></a-table-column>
                <a-table-column title="状态" :width="110"><template #cell="{ record }"><a-tag color="orange">{{ record.statusName || record.status || '待处理' }}</a-tag></template></a-table-column>
                <a-table-column title="操作" :width="180" fixed="right"><template #cell="{ record }"><a-space><a-button size="small" type="primary" :loading="applicationSaving === Number(record.characNo)" @click="changeApplication(record, 'approve')">批准</a-button><a-button size="small" status="danger" :disabled="applicationSaving !== null" @click="changeApplication(record, 'reject')">拒绝</a-button></a-space></template></a-table-column>
              </template>
            </a-table>
          </a-tab-pane>

          <a-tab-pane key="skills" title="公会技能">
            <a-spin :loading="skillsLoading" class="tab-loading">
              <div class="skill-console"><div><span>可用技能点</span><strong>{{ formatNumber(skills.skillPoint) }}</strong><small>仅调整技能点，不直接编辑技能 Blob</small></div><div class="skill-adjust"><a-input-number v-model="skillPointDelta" :min="-65535" :max="65535" :precision="0" hide-button placeholder="增加或扣减" /><a-button type="primary" :loading="skillSaving" :disabled="!skillPointDelta" @click="saveSkillPoints"><template #icon><icon-swap /></template>调整技能点</a-button></div></div>
              <a-table :data="skills.list" :pagination="false" :scroll="{ x: 680 }"><template #empty><a-empty description="当前仅提供公会技能点只读摘要，技能 Blob 明细尚未解析。" /></template><template #columns><a-table-column title="技能" :width="240"><template #cell="{ record }"><strong>{{ record.label || record.name || `技能 ${record.skillId || '--'}` }}</strong><small class="block-muted">{{ record.description || '' }}</small></template></a-table-column><a-table-column title="当前等级" :width="120"><template #cell="{ record }">LV.{{ record.level ?? '--' }}</template></a-table-column><a-table-column title="最高等级" :width="120"><template #cell="{ record }">LV.{{ record.maxLevel ?? '--' }}</template></a-table-column><a-table-column title="状态" :width="130"><template #cell="{ record }"><a-tag :color="record.available === false ? 'gray' : 'green'">{{ record.available === false ? '不可识别' : '只读' }}</a-tag></template></a-table-column></template></a-table>
            </a-spin>
          </a-tab-pane>

          <a-tab-pane key="announcement" title="公会公告">
            <a-spin :loading="announcementLoading" class="tab-loading">
              <div class="announcement-editor"><div class="tab-copy"><strong>公会公告</strong><span>公告最多 200 个字符，保存后立即写入公会公告表。</span></div><a-textarea v-model="announcement.content" :max-length="200" show-word-limit :auto-size="{ minRows: 8, maxRows: 16 }" placeholder="输入公会公告" /><div class="settings-actions"><a-button type="primary" :loading="announcementSaving" :disabled="announcement.content === announcement.expectedContent" @click="saveAnnouncement"><template #icon><icon-save /></template>保存公告</a-button></div></div>
            </a-spin>
          </a-tab-pane>

          <a-tab-pane key="contributions" title="贡献记录">
            <div class="member-toolbar"><div class="tab-copy"><strong>成员贡献</strong><span>修改采用旧值校验并记录后台审计。</span></div><a-button :loading="contributionLoading" @click="loadContributions"><template #icon><icon-refresh /></template>刷新</a-button></div>
            <a-table :data="contributions.list" :loading="contributionLoading" :pagination="false" :scroll="{ x: 820 }">
              <template #empty><a-empty description="暂无贡献记录" /></template>
              <template #columns>
                <a-table-column title="角色" :width="220"><template #cell="{ record }"><button class="member-profile-link simple" type="button" @click="openPlayerInspector(Number(record.characNo), 'guild', '公会贡献')"><strong>{{ record.characName || '--' }}</strong><small>#{{ record.characNo }}</small></button></template></a-table-column>
                <a-table-column title="当前贡献" :width="150"><template #cell="{ record }"><span class="member-point">{{ formatNumber(record.memberPoint ?? record.contribution) }}</span></template></a-table-column>
                <a-table-column title="修改为" :width="200"><template #cell="{ record }"><a-input-number :model-value="contributionDrafts[record.characNo] ?? Number(record.memberPoint ?? record.contribution ?? 0)" :min="0" :max="4294967295" hide-button @change="(value) => contributionDrafts[record.characNo] = Number(value)" /></template></a-table-column>
                <a-table-column title="最后游戏" :width="190"><template #cell="{ record }">{{ record.lastPlayTime || record.lastContributionAt || record.updatedAt || '--' }}</template></a-table-column>
                <a-table-column title="操作" :width="90" fixed="right"><template #cell="{ record }"><a-tooltip content="保存成员贡献"><a-button type="primary" shape="circle" :loading="contributionSaving === record.characNo" :disabled="contributionSaving !== null || Number(contributionDrafts[record.characNo] ?? record.memberPoint ?? record.contribution ?? 0) === Number(record.memberPoint ?? record.contribution ?? 0)" @click="saveContribution(record)"><icon-save /></a-button></a-tooltip></template></a-table-column>
              </template>
            </a-table>
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

.member-profile-link {
  min-width: 0;
  padding: 0;
  border: 0;
  color: inherit;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.member-profile-link:hover .member-name,
.member-profile-link:hover strong {
  color: var(--gm-cyan);
}

.member-profile-link.simple {
  display: block;
  width: 100%;
}

.member-profile-link.simple strong,
.member-profile-link.simple small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-profile-link.simple small {
  margin-top: 3px;
  color: var(--gm-muted);
  font-size: 10px;
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

.tab-copy {
  min-width: 0;
}

.tab-copy strong,
.tab-copy span {
  display: block;
}

.tab-copy strong {
  color: var(--gm-text);
  font-size: 14px;
}

.tab-copy span {
  margin-top: 4px;
  color: var(--gm-muted);
  font-size: 11px;
}

.tab-loading {
  display: block;
  min-height: 260px;
}

.skill-console {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(300px, auto);
  align-items: end;
  gap: 18px;
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid var(--gm-rule);
  border-left: 3px solid var(--gm-amber);
  border-radius: 6px;
  background: rgba(8, 13, 22, .28);
}

.skill-console span,
.skill-console strong,
.skill-console small {
  display: block;
}

.skill-console span,
.skill-console small {
  color: var(--gm-muted);
  font-size: 11px;
}

.skill-console strong {
  margin: 6px 0;
  color: var(--gm-amber);
  font: 750 23px/1 ui-monospace, Consolas, monospace;
}

.skill-adjust {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) auto;
  gap: 8px;
}

.announcement-editor {
  display: grid;
  gap: 14px;
}

.block-muted {
  display: block;
  margin-top: 4px;
  color: var(--gm-muted);
  font-size: 10px;
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

  .skill-console {
    grid-template-columns: minmax(0, 1fr);
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

  .skill-adjust {
    grid-template-columns: minmax(0, 1fr);
  }

  .skill-adjust .arco-btn {
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
