import Request from './Request';

export type PageResult<T> = {
  page: number;
  pageSize: number;
  totalSize: number;
  list: T[];
};

export type RuntimePlayer = {
  characNo: number;
  characName: string;
  accountName?: string;
  uid?: number;
  level?: number;
  jobName?: string;
  growTypeName?: string;
  gold?: number;
  fatigue?: number;
  mapId?: number;
  mapName?: string;
  party?: boolean;
  guildName?: string;
  trading?: boolean;
  online?: boolean;
  updatedAt?: string;
};

export type RuntimeHealth = {
  status?: 'healthy' | 'degraded' | 'offline' | string;
  observedAt?: number;
  gameProcess?: Record<string, any>;
  checkedAt?: string;
  // Retained for compatibility with pre-v2 health payloads.
  game?: Record<string, any>;
  bridge?: Record<string, any>;
  services?: Array<Record<string, any>>;
  ports?: Array<Record<string, any>>;
  logs?: Array<Record<string, any>>;
  warnings?: string[];
};

export type PlayerProfile = {
  characNo: number;
  characName?: string;
  accountName?: string;
  uid?: number;
  level?: number;
  jobName?: string;
  growTypeName?: string;
  online?: boolean;
  runtime?: Record<string, any> | null;
  confirmationToken?: string;
  targetToken?: string;
  [key: string]: any;
};

export type CurrencyBalance = {
  type: string;
  label?: string;
  balance: number | null;
  editable?: boolean;
  onlineAllowed?: boolean;
  minDelta?: number;
  maxDelta?: number;
  reason?: string;
};

export type ContractRecord = {
  type: string;
  preType?: number;
  eventId?: number;
  label?: string;
  startAt?: string;
  endAt?: string;
  active?: boolean;
  runtimeActive?: boolean | null;
  level?: number;
  description?: string;
};

export type InventorySlot = {
  slot: number;
  itemId?: number;
  itemName?: string;
  icon?: Record<string, any>;
  rarity?: number;
  quantity?: number;
  level?: number;
  category?: string;
  unavailable?: boolean;
  reason?: string;
  [key: string]: any;
};

export type PlayerInventory = {
  bags?: Array<{ key: string; label: string; slots: InventorySlot[] }>;
  runtimeMatched?: boolean | null;
  warning?: string;
};

export type OperationResponse<T = any> = {
  requestId?: string;
  before?: T;
  after?: T;
  syncStatus?: string;
  auditId?: string | number;
  message?: string;
  [key: string]: any;
};

export type AnalyticsFilters = {
  startDate: string;
  endDate: string;
  metric?: string;
  metrics?: string[];
  groupBy?: string;
};

export type AnalyticsMetricOption = {
  value: string;
  label: string;
  unit?: string;
  supported?: boolean;
  groups?: string[];
};

const toQuery = (params: Record<string, unknown> = {}) => {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    if (Array.isArray(value)) value.forEach((entry) => query.append(key, String(entry)));
    else query.set(key, String(value));
  });
  const text = query.toString();
  return text ? `?${text}` : '';
};

const data = async <T>(request: Promise<any>): Promise<T> => (await request).data as T;

const operationRequestId = (payload: Record<string, unknown>) =>
  String(payload.requestId || globalThis.crypto?.randomUUID?.() || `gm-${Date.now()}-${Math.random().toString(16).slice(2)}`);

const operationConfig = (payload: Record<string, unknown>) => ({
  headers: { requestId: operationRequestId(payload) },
});

const issueTargetToken = (characNo: number) =>
  data<{ targetToken: string; expiresAt?: number }>(Request.post(`/api/v1/gm/players/${characNo}/target-token`));

const contractLabels: Record<number, string> = {
  9: '成长之契约',
  10: '霸王之契约',
  11: '达人之契约',
  22: '晶体契约',
};

const analyticsQuery = (filters: AnalyticsFilters) => ({
  start: filters.startDate,
  end: filters.endDate,
  metric: filters.metric || filters.metrics?.[0] || 'new_characters',
  groupBy: filters.groupBy || 'none',
});

export const gmOperations = {
  getOnline: async (filters: Record<string, unknown> = {}) => {
    const page = Math.max(1, Number(filters.page || 1));
    const pageSize = Math.max(1, Number(filters.pageSize || 20));
    const payload = await data<any>(Request.get(`/api/v1/gm/runtime/online${toQuery({
      keyword: filters.keyword,
      offset: (page - 1) * pageSize,
      limit: pageSize,
    })}`));
    return {
      ...payload,
      page,
      pageSize,
      totalSize: Number(payload?.total ?? payload?.totalSize ?? 0),
      warning: payload?.status && payload.status !== 'AVAILABLE' ? payload.reason : '',
      list: Array.isArray(payload?.list) ? payload.list : [],
    } as PageResult<RuntimePlayer> & Record<string, any>;
  },
  getRuntimeHealth: () => data<RuntimeHealth>(Request.get('/api/v1/gm/runtime/health')),

  getPlayerProfile: async (characNo: number) => {
    const payload = await data<Record<string, any>>(Request.get(`/api/v1/gm/players/${characNo}`));
    return {
      ...payload,
      accountName: payload.accountName || payload.accountname,
      uid: Number(payload.uid ?? payload.accountId ?? 0) || undefined,
      online: payload.online ?? payload.runtime?.online ?? false,
    } as PlayerProfile;
  },
  issueTargetToken,
  getPlayerCurrency: async (characNo: number) => {
    const [snapshot, options] = await Promise.all([
      data<Record<string, any>>(Request.get(`/api/v1/gm/players/${characNo}/currency`)),
      data<Array<Record<string, any>>>(Request.get('/api/v1/gm/players/currency/options')),
    ]);
    const balances = (options || []).map((option) => {
      const type = String(option.value || '');
      const rawBalance = snapshot?.[type];
      const editable = rawBalance !== null && rawBalance !== undefined;
      const balance = editable ? Number(rawBalance) : null;
      return {
        type,
        label: option.label || type,
        balance,
        editable,
        onlineAllowed: option.onlineMode !== 'OFFLINE_ONLY',
        minDelta: balance === null ? undefined : -balance,
        maxDelta: balance === null ? undefined : Math.max(0, 4294967295 - balance),
        reason: editable ? '' : snapshot?.reason || '数据源暂不可用',
      } as CurrencyBalance;
    });
    return { ...snapshot, balances };
  },
  changeCurrency: async (characNo: number, payload: Record<string, unknown>) => {
    const requestId = operationRequestId(payload);
    const token = await issueTargetToken(characNo);
    return data<OperationResponse>(Request.put(`/api/v1/gm/players/${characNo}/currency/change`, {
      currency: payload.currency || payload.type,
      delta: payload.delta,
      targetToken: token.targetToken,
    }, { headers: { requestId } }));
  },
  getPlayerContracts: async (characNo: number) => {
    const payload = await data<Record<string, any>>(Request.get(`/api/v1/gm/players/${characNo}/contracts`));
    const runtimeTypes = Array.isArray(payload?.runtime?.activePremiumTypes) ? payload.runtime.activePremiumTypes.map(Number) : [];
    const contracts = (Array.isArray(payload?.contracts) ? payload.contracts : []).map((row: any) => {
      const preType = Number(row.preType ?? row.type ?? 0);
      return {
        ...row,
        type: String(preType),
        preType,
        label: contractLabels[preType] || `契约 ${preType}`,
        startAt: row.serviceStart ?? row.startAt,
        endAt: row.serviceEnd ?? row.endAt,
        active: row.state === 'ACTIVE' || row.active === true,
        runtimeActive: payload?.runtime?.status === 'UNAVAILABLE' ? null : runtimeTypes.includes(preType),
      } as ContractRecord;
    });
    return {
      ...payload,
      contracts,
      options: (payload?.supportedTypes || []).map((value: number) => ({ value: String(value), label: contractLabels[value] || `契约 ${value}` })),
    };
  },
  changePlayerContract: async (characNo: number, payload: Record<string, unknown>) => {
    const requestId = operationRequestId(payload);
    const token = await issueTargetToken(characNo);
    const operation = payload.action === 'grant' ? 'issue' : payload.action;
    return data<OperationResponse>(Request.put(`/api/v1/gm/players/${characNo}/contracts`, {
      operation,
      preType: Number(payload.preType ?? payload.type),
      eventId: payload.eventId,
      durationDays: payload.durationDays ?? payload.days,
      // The contract endpoint uses durationDays when issuing and days when
      // extending or shortening an existing record.
      days: payload.days ?? payload.durationDays,
      serverId: payload.serverId,
      targetToken: token.targetToken,
    }, { headers: { requestId } }));
  },
  getPlayerInventory: async (characNo: number) => {
    const payload = await data<Record<string, any>>(Request.get(`/api/v1/gm/players/${characNo}/inventory`));
    const normalizeSlots = (slots: any[]) => (slots || []).map((slot) => ({
      ...slot,
      itemName: slot.itemName || slot.name,
      unavailable: slot.available === false,
      reason: slot.reason || slot.error,
    }));
    return {
      ...payload,
      bags: [
        { key: 'equipment', label: '已穿戴装备', slots: normalizeSlots(payload.equipment) },
        { key: 'inventory', label: '角色背包', slots: normalizeSlots(payload.inventory) },
        { key: 'creatureSlots', label: '宠物栏', slots: normalizeSlots(payload.creatureSlots) },
        { key: 'avatarItems', label: '装扮物品', slots: normalizeSlots(payload.avatarItems) },
        { key: 'creatureItems', label: '宠物物品', slots: normalizeSlots(payload.creatureItems) },
      ].filter((group) => group.slots.length),
    } as PlayerInventory;
  },
  getPlayerMailRewards: (characNo: number) => data<Record<string, any>>(Request.get(`/api/v1/gm/players/${characNo}/mail-rewards`)),
  getPlayerGuildActivity: (characNo: number) => data<Record<string, any>>(Request.get(`/api/v1/gm/players/${characNo}/guild-activity`)),
  getPlayerSecurityAudit: async (characNo: number, filters: Record<string, unknown> = {}) => {
    const payload = await data<Record<string, any>>(Request.get(`/api/v1/gm/players/${characNo}/security-audit`));
    const list = Array.isArray(payload?.audit) ? payload.audit : [];
    return {
      page: Number(filters.page || 1),
      pageSize: Number(filters.pageSize || 100),
      totalSize: list.length,
      list,
      security: payload?.security,
      securitySource: payload?.securitySource,
      auditSource: payload?.auditSource,
    };
  },

  getAnalyticsMetrics: () => data<AnalyticsMetricOption[]>(Request.get('/api/v1/gm/analytics/metrics')),
  getAnalyticsReport: (filters: AnalyticsFilters) => data<Record<string, any>>(Request.get(`/api/v1/gm/analytics/report${toQuery(analyticsQuery(filters))}`)),
  analyticsExportUrl: (filters: AnalyticsFilters) => `/api/v1/gm/analytics/export${toQuery(analyticsQuery(filters))}`,
  downloadAnalyticsCsv: async (filters: AnalyticsFilters) => {
    const response = await (Request.get as any)(`/api/v1/gm/analytics/export${toQuery(analyticsQuery(filters))}`, { responseType: 'blob' });
    return response.data as Blob;
  },

  getGuildApplications: (guildId: number) => data<PageResult<Record<string, any>>>(Request.get(`/api/v1/gm/guilds/${guildId}/applications`)),
  changeGuildApplication: (guildId: number, applicationId: number, action: 'approve' | 'reject', payload: Record<string, unknown> = {}) =>
    data<OperationResponse>(Request.post(`/api/v1/gm/guilds/${guildId}/applications/${applicationId}/${action}`, payload, operationConfig(payload))),
  getGuildSkills: async (guildId: number) => {
    const payload = await data<Record<string, any>>(Request.get(`/api/v1/gm/guilds/${guildId}/skills`));
    return {
      ...payload,
      skillPoint: Number(payload?.remainSp || 0),
      list: payload?.list || payload?.skills || [],
    };
  },
  changeGuildSkillPoints: (guildId: number, payload: Record<string, unknown>) => {
    const expected = Number(payload.expectedSkillPoint || 0);
    const remainSp = expected + Number(payload.delta || 0);
    return data<OperationResponse>(Request.put(`/api/v1/gm/guilds/${guildId}/skills/points`, { remainSp, expectedRemainSp: expected }, operationConfig(payload)));
  },
  getGuildAnnouncement: (guildId: number) => data<Record<string, any>>(Request.get(`/api/v1/gm/guilds/${guildId}/announcement`)),
  updateGuildAnnouncement: (guildId: number, payload: Record<string, unknown>) =>
    data<OperationResponse>(Request.put(`/api/v1/gm/guilds/${guildId}/announcement`, { notice: payload.content ?? payload.notice }, operationConfig(payload))),
  getGuildContributions: (guildId: number, filters: Record<string, unknown> = {}) =>
    data<PageResult<Record<string, any>>>(Request.get(`/api/v1/gm/guilds/${guildId}/members${toQuery(filters)}`)),
  changeGuildContribution: (guildId: number, characNo: number, payload: Record<string, unknown>) =>
    data<OperationResponse>(Request.put(`/api/v1/gm/guilds/${guildId}/members/${characNo}/contribution`, {
      memberPoint: payload.contribution,
      expectedMemberPoint: payload.expectedContribution,
    }, operationConfig(payload))),
  removeGuildMember: (guildId: number, characNo: number, payload: Record<string, unknown>) =>
    data<OperationResponse>(Request.delete(`/api/v1/gm/guilds/${guildId}/members/${characNo}`, operationConfig(payload))),
};

export { toQuery };
