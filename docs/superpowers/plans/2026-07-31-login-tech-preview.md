# 动态科技风登录页预览实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有登录页改造成可本地预览的动态科技风页面，同时保持登录行为不变。

**Architecture:** 在现有 `Login.vue` 内隔离 Canvas 背景生命周期、登录表单状态和响应式样式。Canvas 只输出视觉背景，登录仍通过现有 `Request` 与路由完成。

**Tech Stack:** Vue 3、TypeScript、Arco Design、Canvas 2D、Less、Vite、Playwright CLI。

---

### Task 1: 建立登录页结构验收检查

**Files:**
- Create: `webui2/scripts/verify-login-preview.mjs`
- Modify: `webui2/package.json`

- [ ] **Step 1: 编写当前必然失败的结构检查**

检查脚本读取 `src/views/Login.vue`，断言旧背景引用已经移除，并要求存在 Canvas、减少动态效果、表单提交、加载状态、自动填充和移动端断点标记。

```js
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const source = await readFile(new URL('../src/views/Login.vue', import.meta.url), 'utf8');
assert.doesNotMatch(source, /login-bg(?:2)?\.png/);
assert.match(source, /<canvas[^>]+ref="sceneCanvas"/);
assert.match(source, /prefers-reduced-motion/);
assert.match(source, /@submit\.prevent="login"/);
assert.match(source, /:loading="submitting"/);
assert.match(source, /autocomplete="username"/);
assert.match(source, /autocomplete="current-password"/);
assert.match(source, /100dvh/);
assert.match(source, /@media \(max-width: 720px\)/);
console.log('login preview structure verified');
```

- [ ] **Step 2: 增加并运行检查命令**

在 `package.json` 增加：

```json
"verify:login": "node scripts/verify-login-preview.mjs"
```

运行：`npm run verify:login`

预期：检查因旧背景引用和缺少 Canvas 等条件失败。

### Task 2: 实现动态科技风登录页

**Files:**
- Modify: `webui2/src/views/Login.vue`
- Test: `webui2/scripts/verify-login-preview.mjs`

- [ ] **Step 1: 增加 Canvas 生命周期和登录状态**

在脚本中使用 `ref`、`onMounted`、`onBeforeUnmount` 管理画布、动画帧、窗口尺寸和降低动态效果监听。Canvas 根据设备像素比缩放，并限制节点数量。

```ts
const sceneCanvas = ref<HTMLCanvasElement | null>(null);
const submitting = ref(false);
const statusText = ref('等待身份验证');
let animationFrame = 0;
let stopScene: (() => void) | undefined;

onMounted(() => {
  stopScene = startScene(sceneCanvas.value);
});

onBeforeUnmount(() => {
  cancelAnimationFrame(animationFrame);
  stopScene?.();
});
```

- [ ] **Step 2: 保持原登录行为并补充重复提交保护**

```ts
const login = async () => {
  if (submitting.value || !form.account.trim() || !form.password) return;
  submitting.value = true;
  statusText.value = '正在建立安全会话';
  try {
    const res = await Request.post('api/v1/login', form);
    localStorage.setItem('token', res.data);
    await router.replace({
      path: router.currentRoute.value.query.redirect as string || '/admin/dashboard',
    });
  } finally {
    submitting.value = false;
    statusText.value = '等待身份验证';
  }
};
```

- [ ] **Step 3: 重构模板**

模板包含全屏 Canvas、左侧品牌区、右侧登录区、固定标签输入框、重置按钮和登录按钮。表单使用 `@submit.prevent="login"`，输入框设置正确的 `name` 与 `autocomplete`。

- [ ] **Step 4: 完成响应式科技主题样式**

使用单一电光青强调色、深色材质、稳定宽度和 `min-height: 100dvh`。增加 `prefers-reduced-motion`、`prefers-reduced-transparency` 与 `max-width: 720px` 样式，不引用旧图片。

- [ ] **Step 5: 运行结构检查和生产构建**

运行：

```bash
npm run verify:login
npm run build
```

预期：结构检查输出 `login preview structure verified`，生产构建成功。

### Task 3: 本地浏览器验收

**Files:**
- Create: `output/playwright/login-preview-desktop.png`
- Create: `output/playwright/login-preview-mobile.png`

- [ ] **Step 1: 启动本地 Vite 服务**

运行：`npm run dev -- --host 127.0.0.1 --port 4175`

预期：登录页可通过 `http://127.0.0.1:4175/login` 打开。

- [ ] **Step 2: 使用 Playwright 检查桌面视口**

在 1440x1000 视口打开登录页，确认 Canvas 像素非空、无横向滚动、表单可聚焦、按钮不换行，并保存桌面截图。

- [ ] **Step 3: 使用 Playwright 检查移动视口**

在 390x844 视口重复检查，确认品牌区简化、登录面板没有超出屏幕，并保存移动截图。

- [ ] **Step 4: 检查控制台与页面错误**

确认页面没有 Vue、Canvas、资源加载或布局相关错误。

### Task 4: 提交预览

**Files:**
- Modify: `webui2/src/views/Login.vue`
- Modify: `webui2/package.json`
- Create: `webui2/scripts/verify-login-preview.mjs`
- Create: `docs/superpowers/specs/2026-07-31-login-tech-preview-design.md`
- Create: `docs/superpowers/plans/2026-07-31-login-tech-preview.md`

- [ ] **Step 1: 检查改动范围**

运行：`git diff --check` 和 `git status --short`。

- [ ] **Step 2: 提交登录页预览代码**

```bash
git add webui2/src/views/Login.vue webui2/package.json webui2/scripts/verify-login-preview.mjs docs/superpowers/specs/2026-07-31-login-tech-preview-design.md docs/superpowers/plans/2026-07-31-login-tech-preview.md
git commit -m "feat: redesign admin login experience"
```
