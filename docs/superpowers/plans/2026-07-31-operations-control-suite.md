# 网页 GM 综合运营控制套件实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Every behavior change starts with a failing test.

**Goal:** 在保留现有深夜控制台主题与业务流程的前提下，实现实时在线、玩家档案、货币、契约、只读背包、公会增强、运行时健康和运营统计，并将所有修改操作纳入统一权限、幂等与审计。

**Architecture:** Java 后端负责数据库事务、业务聚合、编码转换、PVF 补全、权限与审计；Frida 回环桥接只提供受限的实时快照与在线金币差值操作；Vue 前端通过统一 API 和全局玩家档案页中页组合功能。所有运行时数据允许降级，所有写接口禁止原始 SQL、校验目标令牌并记录修改前后状态。

**Tech Stack:** Java 8、Spring Boot 2.1、JdbcTemplate、MySQL 5、Frida JavaScript、Node.js 测试、Vue 3、TypeScript、Arco Design、Vite、Playwright。

---

### Task 1: 统一权限、请求上下文、审计与原始 SQL 收口

**Files:**
- Create: `src/main/java/com/aiyi/game/dnfserver/service/impl/GmAuthorizationService.java`
- Create: `src/main/java/com/aiyi/game/dnfserver/service/impl/GmOperationAuditService.java`
- Create: `src/main/java/com/aiyi/game/dnfserver/entity/gm/GmOperationContext.java`
- Create: `src/main/resources/db/gm_operation_audit.sql`
- Modify: `src/main/java/com/aiyi/game/dnfserver/controller/DatabaseToolsController.java`
- Modify: `src/main/java/com/aiyi/game/dnfserver/service/impl/GmLogRetentionService.java`
- Test: `src/test/java/com/aiyi/game/dnfserver/service/impl/GmAuthorizationServiceTest.java`
- Test: `src/test/java/com/aiyi/game/dnfserver/service/impl/GmOperationAuditServiceTest.java`
- Test: `src/test/java/com/aiyi/game/dnfserver/controller/DatabaseToolsControllerTest.java`

- [ ] 先写失败测试，覆盖最高管理员判定、普通后台用户拒绝、重复请求号返回原结果、失败审计独立保留和 7 天清理。
- [ ] 新增幂等迁移器，创建 `dnf_service.gm_operation_audit` 及唯一 `request_id`、目标和时间索引。
- [ ] 建立统一操作上下文，记录管理员、可信客户端 IP、模块、动作、目标、请求号和状态。
- [ ] 将浏览器通用 SQL 控制器默认关闭，并在启用时仍要求最高管理员。
- [ ] 运行定向 Java 测试，确认测试由红转绿。

### Task 2: 扩展 Frida 协议和 Java 运行时客户端

**Files:**
- Create: `runtime/frida/gm-runtime-bridge-v2.js`
- Create: `runtime/frida/test-gm-runtime-bridge-v2.js`
- Modify: `src/main/java/com/aiyi/game/dnfserver/service/impl/GameRuntimeClient.java`
- Modify: `src/main/java/com/aiyi/game/dnfserver/service/impl/FridaGameRuntimeClient.java`
- Modify: `src/test/java/com/aiyi/game/dnfserver/service/impl/FridaGameRuntimeClientTest.java`

- [ ] 先扩展 Java 与 Node 失败测试，覆盖 `ping`、`online_snapshot`、`inspect_player`、`currency_snapshot`、`change_gold`、`inventory_snapshot`、`inspect_contracts` 和旧 `add_gold`。
- [ ] 运行时调用全部进入游戏主线程，在线状态只接纳 `state >= 3`，请求与响应设置数量和字节上限。
- [ ] `change_gold` 使用有符号差值、无符号边界、账号角色一致性、精确前后值校验和请求号幂等。
- [ ] 运行时不确定时返回明确降级状态，不自动重试可能已执行的写操作。
- [ ] 运行 Node 和 Java 定向测试，确认兼容旧泡点金币流程。

### Task 3: 玩家聚合档案、目标令牌和只读背包解析

**Files:**
- Create: `src/main/java/com/aiyi/game/dnfserver/controller/GmPlayerController.java`
- Create: `src/main/java/com/aiyi/game/dnfserver/service/impl/GmPlayerProfileService.java`
- Create: `src/main/java/com/aiyi/game/dnfserver/service/impl/GmTargetTokenService.java`
- Create: `src/main/java/com/aiyi/game/dnfserver/service/impl/GmInventoryService.java`
- Create: `src/main/java/com/aiyi/game/dnfserver/entity/gm/PlayerProfile.java`
- Create: `src/main/java/com/aiyi/game/dnfserver/entity/gm/InventorySnapshot.java`
- Test: `src/test/java/com/aiyi/game/dnfserver/service/impl/GmPlayerProfileServiceTest.java`
- Test: `src/test/java/com/aiyi/game/dnfserver/service/impl/GmTargetTokenServiceTest.java`
- Test: `src/test/java/com/aiyi/game/dnfserver/service/impl/GmInventoryServiceTest.java`

- [ ] 先写失败测试，覆盖角色与账号归属、离线降级、中文名称、令牌过期/目标不符、zlib 记录解析和单槽损坏隔离。
- [ ] 聚合账号、角色、活动、公会、邮件、奖励与安全摘要，运行时字段保持可空并携带来源状态。
- [ ] 为危险操作签发短期一次性目标令牌，令牌绑定角色、管理员与过期时间。
- [ ] 只读解析 61 字节 86 版本背包/装备槽，并关联规范化宠物、时装和 PVF 名称、分类、等级、稀有度与图标。
- [ ] 在线背包只做 Frida 数值交叉核对，不提供修改入口。

### Task 4: 货币与契约事务服务

**Files:**
- Create: `src/main/java/com/aiyi/game/dnfserver/service/impl/GmCurrencyService.java`
- Create: `src/main/java/com/aiyi/game/dnfserver/service/impl/GmContractService.java`
- Create: `src/main/java/com/aiyi/game/dnfserver/entity/gm/CurrencyChangeRequest.java`
- Create: `src/main/java/com/aiyi/game/dnfserver/entity/gm/ContractChangeRequest.java`
- Test: `src/test/java/com/aiyi/game/dnfserver/service/impl/GmCurrencyServiceTest.java`
- Test: `src/test/java/com/aiyi/game/dnfserver/service/impl/GmContractServiceTest.java`

- [ ] 先写失败测试，覆盖全部八种货币、正负边界、在线禁用、行锁、请求幂等、契约时间边界和受支持类型白名单。
- [ ] 在线角色金币经 Frida 修改，离线金币经数据库事务与行锁修改；其他货币按各表的无符号范围执行差值修改。
- [ ] 非金币货币在角色在线且无法实时核对时拒绝修改，不提供批量或绝对值覆盖。
- [ ] 契约支持查看、发放、延长、缩短和撤销；数据库写入使用事务/upsert，在线角色标记“重新登录后生效”。
- [ ] 每次写操作验证最高管理员、目标令牌、请求号，并写入修改前后审计。

### Task 5: 实时在线、运行时健康和运营统计

**Files:**
- Create: `src/main/java/com/aiyi/game/dnfserver/controller/GmRuntimeController.java`
- Create: `src/main/java/com/aiyi/game/dnfserver/controller/GmAnalyticsController.java`
- Create: `src/main/java/com/aiyi/game/dnfserver/service/impl/GmRuntimeHealthService.java`
- Create: `src/main/java/com/aiyi/game/dnfserver/service/impl/GmRealtimePlayerService.java`
- Create: `src/main/java/com/aiyi/game/dnfserver/service/impl/GmAnalyticsService.java`
- Test: `src/test/java/com/aiyi/game/dnfserver/service/impl/GmRuntimeHealthServiceTest.java`
- Test: `src/test/java/com/aiyi/game/dnfserver/service/impl/GmRealtimePlayerServiceTest.java`
- Test: `src/test/java/com/aiyi/game/dnfserver/service/impl/GmAnalyticsServiceTest.java`

- [ ] 先写失败测试，覆盖单探针降级、在线真值、搜索/分页、指标白名单、366/367 天边界、缺失数据源和 CSV 公式注入。
- [ ] 实时在线以 Frida 为真值，数据库仅补全账号、中文角色名、等级、职业、公会和活动字段。
- [ ] 健康接口独立探测游戏进程、Frida 延迟、固定端口、Java/nginx/网关服务和白名单日志，单项失败不丢失其他结果。
- [ ] 统计接口提供汇总、趋势、分类、数据源状态和 CSV；只允许枚举指标/分组与参数化日期查询，最大 366 天。
- [ ] 复用现有服务器监控数字刷新机制，不触发整页刷新。

### Task 6: 扩展公会管理事务

**Files:**
- Modify: `src/main/java/com/aiyi/game/dnfserver/controller/GuildManagementController.java`
- Modify: `src/main/java/com/aiyi/game/dnfserver/service/impl/GuildManagementService.java`
- Modify: `src/test/java/com/aiyi/game/dnfserver/service/impl/GuildManagementServiceTest.java`

- [ ] 先写失败测试，覆盖设置并发旧值、申请批准/拒绝、贡献修改、多表移除、公告 upsert、技能点范围和会长保护。
- [ ] 增加经验、点数、资金、公告和剩余技能点读取/修改，保持技能 Blob 只读。
- [ ] 增加入会申请列表、批准、拒绝与贡献修改；批准复用安全添加成员逻辑。
- [ ] 移除成员时在一个事务内同步 `guild_member`、`charac_info`、`guild_info`、`guild_search` 和申请状态。
- [ ] 禁止移除、降级或修改会长，禁止其他成员设为职级 1；所有写操作统一审计。

### Task 7: 前端 API、共享玩家档案和独立页面

**Files:**
- Create: `webui2/src/api/gmOperations.ts`
- Create: `webui2/src/api/entitys/GmOperations.ts`
- Create: `webui2/src/composables/usePlayerInspector.ts`
- Create: `webui2/src/components/PlayerInspector.vue`
- Create: `webui2/src/components/player/PlayerCurrencyPanel.vue`
- Create: `webui2/src/components/player/PlayerContractsPanel.vue`
- Create: `webui2/src/components/player/PlayerInventoryPanel.vue`
- Create: `webui2/src/components/MetricTrendChart.vue`
- Create: `webui2/src/views/admin/player/RealtimePlayers.vue`
- Create: `webui2/src/views/admin/analytics/OperationsAnalytics.vue`
- Create: `webui2/scripts/verify-operations-ui.mjs`
- Modify: `webui2/package.json`

- [ ] 先编写失败的前端结构检查，要求新路由、菜单、共享档案七个标签、局部轮询、降级状态、CSV 和主题层级标记。
- [ ] 实时在线页面使用请求序列防止旧响应覆盖，局部刷新列表与统计，不闪屏。
- [ ] 玩家档案在管理员布局层全局挂载，账号、角色、在线、公会和邮件页面均可打开同一页中页。
- [ ] 货币修改使用差值输入、目标令牌和确认弹窗；契约与背包按既定边界展示。
- [ ] 运营统计提供日期、指标、汇总、趋势、分类、来源状态和 CSV 导出。

### Task 8: 前端合并、主题和响应式验收

**Files:**
- Modify: `webui2/src/router/index.ts`
- Modify: `webui2/src/views/admin/Index.vue`
- Modify: `webui2/src/views/admin/player/ServerMonitor.vue`
- Modify: `webui2/src/views/admin/guild/GuildManager.vue`
- Modify: `webui2/src/views/admin/player/PlayerAccounts.vue`
- Modify: `webui2/src/views/admin/player/PlayerRoles.vue`
- Modify: `webui2/src/views/admin/mail/MailQuery.vue`
- Modify: `webui2/src/views/admin/mail/PlayerMailManager.vue`
- Modify: `webui2/src/styles/admin-theme.css`

- [ ] 增加“实时在线”和“运营统计”菜单与路由，保持登录页极光扫描和数据雨不变。
- [ ] 服务器监控合并运行时健康与服务状态；公会现有弹窗增加设置、成员、申请、技能、公告和贡献标签。
- [ ] 统一页中页、Modal、下拉、Tooltip 和物品提示层级，确保深夜主题下没有白底或被遮挡内容。
- [ ] 修复横向压缩、匿名 resize 监听泄漏和移动端表格宽度，稳定角色名、账号、编号和操作列。
- [ ] 运行结构检查、`vue-tsc` 和生产构建。

### Task 9: 全量验证、打包与服务器部署

**Files:**
- Create: `runtime/frida/install-gm-runtime-bridge-v2.py`
- Create: `output/deployment/deploy-operations-control-suite.py` (local deployment artifact, not committed)
- Create: `output/playwright/operations-*.png` (verification artifact, not committed)

- [ ] 运行全部 Java 测试：显式覆盖 POM 的默认跳过设置。
- [ ] 运行 Frida Node 测试、前端结构检查、类型检查、生产构建和 `git diff --check`。
- [ ] 在本地预览使用桌面与移动端 Playwright 验证正常、空数据、加载、降级和错误状态，并检查 Canvas 非空、控制台错误、横向溢出和弹层遮挡。
- [ ] 部署前备份服务器 JAR、静态资源、Frida 脚本与配置并记录哈希；先部署向后兼容的 Frida，再部署 Java 和静态资源。
- [ ] 验证回环端口、运行时操作、审计幂等、权限、7 天保留、现有功能回归和服务重启后状态；保留可验证回滚包。

