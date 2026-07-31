import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const read = async (relativePath) => {
  try {
    return await readFile(new URL(`../${relativePath}`, import.meta.url), 'utf8');
  } catch {
    return '';
  }
};

const [router, adminIndex, theme, globalTheme, api, inspector, online, analytics, monitor, guild, playerMail] = await Promise.all([
  read('src/router/index.ts'),
  read('src/views/admin/Index.vue'),
  read('src/styles/admin-theme.css'),
  read('src/style.css'),
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
assert.match(adminIndex, /Request\.get\('\/api\/v1\/account\?page=1&pageSize=1'\)/);
assert.match(adminIndex, /document\.body\.classList\.add\('admin-theme'\)/);
assert.match(adminIndex, /document\.body\.classList\.remove\('admin-theme'\)/);
assert.doesNotMatch(adminIndex, /style="color:\s*white/);
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
assert.match(theme, /\.admin-page \.arco-picker/);
assert.match(theme, /\.admin-page \.arco-input-tag/);
assert.match(theme, /\.admin-page \.arco-input-number-input[\s\S]*?background-color:\s*transparent/);
assert.match(theme, /-webkit-box-shadow:\s*0 0 0 1000px var\(--gm-surface-raised\) inset/);
assert.match(theme, /--color-bg-popup:\s*#101a2a/);
assert.match(theme, /--color-bg-5:\s*#152238/);
assert.match(globalTheme, /\.arco-select-dropdown \.arco-select-option/);
assert.match(globalTheme, /\.arco-picker-popup \.arco-calendar-panel/);
assert.match(globalTheme, /\.arco-drawer-container \.arco-table-th/);
assert.match(globalTheme, /\.arco-drawer-container \.arco-tag-checked\.arco-tag-green/);
assert.match(globalTheme, /body\.admin-theme[\s\S]*?background:\s*var\(--gm-bg\)/);
assert.match(globalTheme, /body\.admin-theme input[\s\S]*?color:\s*inherit/);

console.log('operations control UI structure verified');
