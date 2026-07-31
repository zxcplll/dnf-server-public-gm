import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const read = async (relativePath) => {
  try {
    return await readFile(new URL(`../${relativePath}`, import.meta.url), 'utf8');
  } catch {
    return '';
  }
};

const [router, adminIndex, theme, api, inspector, online, analytics, monitor, guild, playerMail] = await Promise.all([
  read('src/router/index.ts'),
  read('src/views/admin/Index.vue'),
  read('src/styles/admin-theme.css'),
  read('src/api/gmOperations.ts'),
  read('src/components/admin/player/PlayerInspector.vue'),
  read('src/views/admin/player/RealtimePlayers.vue'),
  read('src/views/admin/analytics/OperationsAnalytics.vue'),
  read('src/views/admin/player/ServerMonitor.vue'),
  read('src/views/admin/guild/GuildManager.vue'),
  read('src/views/admin/mail/PlayerMailManager.vue'),
]);

assert.match(router, /path:\s*'online'/, '实时在线路由缺失');
assert.match(router, /path:\s*'analytics'/, '运营统计路由缺失');
assert.match(adminIndex, /实时在线/);
assert.match(adminIndex, /运营统计/);
assert.match(api, /runtime\/online/);
assert.match(api, /runtime\/health/);
assert.match(api, /players\/\$\{characNo\}/);
assert.match(api, /target-token/);
assert.match(api, /headers:\s*\{\s*requestId/);
assert.match(api, /Request\.put\(`\/api\/v1\/gm\/players\/\$\{characNo\}\/currency\/change/);
assert.match(api, /Request\.put\(`\/api\/v1\/gm\/players\/\$\{characNo\}\/contracts/);
assert.match(api, /analytics\/metrics/);
assert.match(api, /analytics\/report/);
assert.match(api, /expectedRemainSp/);
assert.match(api, /days:\s*payload\.days\s*\?\?\s*payload\.durationDays/);
assert.match(inspector, /基本资料/);
assert.match(inspector, /货币/);
assert.match(inspector, /契约/);
assert.match(inspector, /背包与装备/);
assert.match(inspector, /邮件与奖励/);
assert.match(inspector, /公会与活动/);
assert.match(inspector, /安全信息/);
assert.match(inspector, /runtime\?\.status === 'AVAILABLE'/);
assert.match(inspector, /runtimeBoolean/);
assert.match(inspector, /moneyOrUnavailable/);
assert.match(online, /online_snapshot|runtime\/online/);
assert.match(online, /setInterval|setTimeout/);
assert.match(analytics, /CSV|csv/i);
assert.match(analytics, /date\.getFullYear\(\)/);
assert.match(analytics, /sourceUnavailable \? '--'/);
assert.match(monitor, /runtime\/health/);
assert.match(monitor, /gameProcess\s*\|\|\s*health\.value\.game/);
assert.match(monitor, /rssBytes/);
assert.match(monitor, /health\.observedAt/);
assert.match(monitor, /if \(amount >= mebibyte\) return/);
assert.match(guild, /申请/);
assert.match(guild, /技能/);
assert.match(guild, /公告/);
assert.match(guild, /贡献/);
assert.match(playerMail, /openPlayerInspector/);
assert.match(theme, /gm-z-modal/);
assert.match(theme, /gm-z-inspector/);
assert.match(theme, /gm-z-hover/);
assert.match(theme, /max-width:\s*860px/);

console.log('operations control UI structure verified');
