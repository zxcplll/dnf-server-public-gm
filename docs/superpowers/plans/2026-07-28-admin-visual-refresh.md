# DNF Admin 视觉刷新 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保持既有路由、接口、权限和业务操作不变的前提下，把 DNF GM 后台改造成“深夜控制室”视觉，并消除桌面、移动端和浮层遮挡。

**Architecture:** 继续使用 Vue 3、Arco Design 和现有路由。新增一层只作用于 `.admin-page` 的主题样式，公共壳层负责唯一页面滚动，选择器与 PVF 详情提示共享视口边界和层级令牌；业务组件只调整布局和样式，不改变请求、轮询、表单提交或分页逻辑。

**Tech Stack:** Vue 3, TypeScript, Arco Design Vue, Less, Vite, Playwright CLI.

---

### Task 1: 建立全局重置与后台主题

**Files:**
- Modify: `webui2/src/style.css`
- Create: `webui2/src/styles/admin-theme.css`
- Modify: `webui2/src/main.ts`

- [ ] **Step 1: 替换 Vite starter reset**

将 `webui2/src/style.css` 改为应用级重置，保留字体抗锯齿，但移除 `body` 居中、`#app` 最大宽度和默认按钮样式：

```css
:root {
  font-family: Inter, "Segoe UI", "Microsoft YaHei", sans-serif;
  color: #dce8f6;
  background: #080d16;
  font-synthesis: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

html, body, #app { width: 100%; min-width: 320px; min-height: 100%; margin: 0; }
body { min-height: 100dvh; overflow-x: hidden; }
#app { max-width: none; padding: 0; text-align: left; }
*, *::before, *::after { box-sizing: border-box; }
button, input, textarea, select { font: inherit; }
a { color: inherit; text-decoration: none; }
```

- [ ] **Step 2: 创建后台主题变量和 Arco 覆盖层**

创建 `webui2/src/styles/admin-theme.css`，以 `.admin-page` 为作用域，定义 `--gm-*` 颜色和层级令牌，并覆盖菜单、页签、卡片、表格、输入框、按钮、滚动条和通知的背景、边框、焦点态。基础规则必须包含：

```css
.admin-page {
  --gm-bg: #080d16;
  --gm-surface: #101a2a;
  --gm-surface-raised: #152238;
  --gm-rule: rgba(151, 174, 204, .18);
  --gm-text: #edf5ff;
  --gm-muted: #8fa4bf;
  --gm-cyan: #4de4d2;
  --gm-amber: #ffbc68;
  --gm-blue: #6d9dff;
  --gm-z-header: 100;
  --gm-z-tabs: 90;
  --gm-z-drawer: 1100;
  --gm-z-dialog: 1200;
  --gm-z-hover: 1300;
  --gm-z-notice: 1400;
  color: var(--gm-text);
  background: var(--gm-bg);
}

.admin-page .arco-card,
.admin-page .arco-table-container,
.admin-page .arco-form { border-color: var(--gm-rule); background: var(--gm-surface); }
.admin-page .arco-input-wrapper,
.admin-page .arco-select-view,
.admin-page .arco-input-number { border-color: var(--gm-rule); background: var(--gm-surface-raised); color: var(--gm-text); }
.admin-page .arco-btn-primary { color: #061118; background: var(--gm-cyan); border-color: var(--gm-cyan); }
.admin-page .arco-btn-primary:hover { background: #70f2e2; border-color: #70f2e2; }
.admin-page .arco-scrollbar-track-direction-vertical .arco-scrollbar-thumb-bar { background: rgba(151, 174, 204, .32); }
```

为 Arco 的 portal 类设置 `z-index: var(--gm-z-dialog)`，通知使用 `var(--gm-z-notice)`；不要再使用组件内的 `9999`。

- [ ] **Step 3: 按导入顺序启用重置和主题**

在 `webui2/src/main.ts` 中把 import 区域调整为：

```ts
import '@arco-design/web-vue/dist/arco.css';
import './style.css';
import './styles/admin-theme.css';
```

主题必须在 Arco CSS 之后加载，路由和插件注册顺序保持不变。

- [ ] **Step 4: 运行前端构建**

Run: `npm run build` (workdir `webui2`)

Expected: Vite 输出 `dist` 且退出码为 0，无 TypeScript 或 Less 错误。

### Task 2: 修复后台壳层、页签和移动导航

**Files:**
- Modify: `webui2/src/views/admin/Index.vue`
- Modify: `webui2/src/components/RecursiveMenuItem.vue`

- [ ] **Step 1: 增加窄屏导航状态和路由同步**

在 `Index.vue` 的 `<script setup>` 中增加 `mobileNavOpen`、`matchMedia('(max-width: 860px)')` 监听和卸载清理；进入菜单后设置 `mobileNavOpen.value = false`。初始化和路由变化使用 `router.currentRoute.value.path.replace(/^\/admin/, '')`，不要再对 `fullPath` 做固定长度截取。保留现有 `onClickMenuItem` 的路由 push、历史页签和面包屑逻辑。

- [ ] **Step 2: 增加抽屉遮罩和稳定的壳层 class**

给 `a-layout-sider` 增加 `class="admin-sider"` 和 `:class="{ 'mobile-open': mobileNavOpen }"`，给折叠按钮增加 `aria-label`，并在侧栏后插入：

```vue
<button
  v-if="mobileNavOpen"
  class="mobile-nav-backdrop"
  type="button"
  aria-label="关闭导航"
  @click="mobileNavOpen = false"
></button>
```

页签保留原关闭和切换事件，但为容器增加 `class="layout-tabs"`，内容滚动只由 `.content-view` 内的 `a-scrollbar` 承担。

- [ ] **Step 3: 应用单滚动容器和响应式尺寸**

在 `Index.vue` 的 scoped Less 中落实以下结构规则：`.admin-page` 使用 `width: 100%; height: 100dvh; min-height: 100dvh; overflow: hidden`；`.admin-layout`、`.layout-right`、`.layout-content`、`.content-view` 添加 `min-width: 0; min-height: 0`；header 和 tabs 使用固定 flex basis；去掉 `.content-view` 与内层 scrollbar 的重复 `overflow: auto`。窄屏下侧栏固定在左侧、抽屉层级为 `var(--gm-z-drawer)`，内容添加 12px 内边距，页签导航横向滚动且标题省略。

- [ ] **Step 4: 稳定菜单项图标和文本布局**

在 `RecursiveMenuItem.vue` 给普通菜单项和子菜单标题加 `.menu-entry` 包裹层，图标与标题使用 `gap: 10px`，标题使用 `min-width: 0; overflow: hidden; text-overflow: ellipsis`。菜单路径生成改为不修改 `props.item` 的纯函数，保持输出路径格式不变。

- [ ] **Step 5: 构建并检查布局类型**

Run: `npm run build` (workdir `webui2`)

Expected: 构建通过；`Index.vue`、`RecursiveMenuItem.vue` 无模板类型错误。

### Task 3: 统一首页和监控页面的高密度视觉

**Files:**
- Modify: `webui2/src/views/admin/Dashboard.vue`
- Modify: `webui2/src/views/admin/player/ServerMonitor.vue`

- [ ] **Step 1: 保留请求和局部刷新行为**

不改两个页面的 endpoint、轮询间隔和数据字段。继续使用 `loading && !hasLoaded` 作为初次加载条件，后续轮询只更新数字和曲线，不显示整页遮罩。

- [ ] **Step 2: 调整响应式栅格**

首页统计块使用 `:xs="24" :sm="12" :lg="6"`，名单使用 `:xs="24" :lg="12"`；监控指标使用相同的断点，图表和在线名单使用 `:xs="24" :lg="12"`。这样 390px 视口不会把四列压成不可读的窄条。

- [ ] **Step 3: 重写 scoped Less 的表面样式**

首页欢迎区改为无大面积渐变的墨色状态横幅，统计块和名单卡片使用 `var(--gm-surface)`、`var(--gm-rule)`、青色/琥珀状态点；监控卡片、图表 tooltip、空状态和更新时间标签使用同一套变量。所有卡片圆角保持 8px，正文和数字保持明确的字号层级。

- [ ] **Step 4: 运行构建和静态检查**

Run: `npm run build` (workdir `webui2`) and `git diff --check`

Expected: 两个命令均退出码为 0；没有新增尾随空格或 Less 解析错误。

### Task 4: 修复物品选择器和 PVF 详情提示的视口边界

**Files:**
- Modify: `webui2/src/components/ItemPicker.vue`
- Modify: `webui2/src/views/admin/pvf/PvfManager.vue`

- [ ] **Step 1: 让 ItemPicker 根据上下空间翻转**

在 `updatePopupHeights` 中同时计算触发器上方和下方可用高度；当下方不足 `MIN_PANEL_HEIGHT` 且上方更大时，将 `popupPlacement` 设置为 `'tl'`，否则使用 `'bl'`。将 `MIN_PANEL_HEIGHT` 调整为 220，并把实际高度限制在可用空间、520px 和视口高度减 24px 三者的最小值。

- [ ] **Step 2: 将宽度限制在真实视口内**

`popupStyle` 使用 `Math.min(PANEL_WIDTH, Math.max(260, viewportWidth - 24))`，同时在 `viewportWidth < 284` 时使用 `Math.max(0, viewportWidth - 24)`，不能让最小宽度反过来撑出屏幕。模板把 `<a-trigger position="bl">` 改成 `:position="popupPlacement"`。

- [ ] **Step 3: 统一提示层级和动态尺寸**

ItemPicker 与 PvfManager 的 `.hover-panel` 使用 `z-index: var(--gm-z-hover)`、`max-height: calc(100dvh - 24px)` 和 `width: min(420px, calc(100vw - 24px))`。保留现有鼠标位置翻转逻辑，确保面板右下角、左下角、右上角和左上角都通过 `Math.max/Math.min` 夹在视口内。

- [ ] **Step 4: 构建并做组件级回归**

Run: `npm run build` (workdir `webui2`)

Expected: 构建通过；ItemPicker 和 PvfManager 的选择、搜索、分页、悬停详情和关闭事件没有改变。

### Task 5: 浏览器验收、修复实际遮挡并打包

**Files:**
- Modify: 仅在浏览器验收发现具体遮挡时修改对应页面的 scoped style；优先使用 `webui2/src/styles/admin-theme.css`，不复制业务逻辑。
- Build output: `webui2/dist`

- [ ] **Step 1: 启动 Vite 开发服务**

Run: `npm run dev -- --host 127.0.0.1 --port 4174` (workdir `webui2`)

Expected: 输出可访问的本地 URL；若 4174 被占用，使用 Vite 输出的备用端口。

- [ ] **Step 2: 用 Playwright 覆盖三个视口**

先执行 `npx --version`，再使用 `C:\Users\YU\.codex\skills\playwright\scripts\playwright_cli.sh` 打开 URL，依次调整到 `1440x900`、`1280x800`、`390x844`。每个视口先执行 snapshot，再截图首页、服务器监控和一个表格页。

- [ ] **Step 3: 验收交互与遮挡**

登录后打开首页、监控、角色管理、全服奖励、邮件查询、PVF 管理和数据库页面；检查侧栏抽屉、页签横滚、表格横滚、Arco 下拉/弹窗、ItemPicker 搜索与分页、物品详情四角定位。浏览器控制台不得出现新增异常；截图不得出现横向溢出、固定元素互相遮挡或内容被裁剪。

- [ ] **Step 4: 产出生产构建并保留回滚点**

Run: `npm run build` (workdir `webui2`); then record `Get-FileHash webui2/dist/assets/* -Algorithm SHA256` for the generated bundle. Keep the existing server static backup before uploading the new `dist` contents.

- [ ] **Step 5: Commit only frontend changes**

使用 `git status --short` 确认后，按文件路径提交本次主题、壳层和弹层改造；不要把既有 Java 编码修复或用户已修改的 `PvfManager.vue` 无关差异回滚或混入提交。
