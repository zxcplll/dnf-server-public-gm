<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import Request from '../api/Request.ts';
import router from '../router';

interface SceneNode {
  x: number;
  y: number;
  vx: number;
  vy: number;
  phase: number;
}

const form = reactive({
  account: '',
  password: '',
});

const sceneCanvas = ref<HTMLCanvasElement | null>(null);
const submitting = ref(false);
const statusText = ref('等待身份验证');
const errorText = ref('');

let stopScene: (() => void) | undefined;

const resetForm = () => {
  if (submitting.value) return;
  form.account = '';
  form.password = '';
  errorText.value = '';
  statusText.value = '等待身份验证';
};

const clearError = () => {
  errorText.value = '';
};

const focusField = (id: string) => {
  document.getElementById(id)?.focus();
};

const login = async () => {
  if (submitting.value) return;
  if (!form.account.trim() || !form.password) {
    errorText.value = '请输入管理员账号和密码';
    return;
  }

  submitting.value = true;
  errorText.value = '';
  statusText.value = '正在建立管理会话';
  try {
    const response = await Request.post('api/v1/login', {
      account: form.account.trim(),
      password: form.password,
    });
    localStorage.setItem('token', response.data);
    statusText.value = '身份验证通过';
    await router.replace({
      path: router.currentRoute.value.query.redirect as string || '/admin/dashboard',
    });
  } catch (error) {
    errorText.value = '身份验证失败，请检查账号、密码或服务连接';
    statusText.value = '等待身份验证';
  } finally {
    submitting.value = false;
  }
};

const startScene = (canvas: HTMLCanvasElement | null) => {
  if (!canvas) return () => undefined;
  const context = canvas.getContext('2d');
  if (!context) return () => undefined;

  const motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
  let animationFrame = 0;
  let width = 0;
  let height = 0;
  let nodes: SceneNode[] = [];
  let disposed = false;

  const resize = () => {
    const bounds = canvas.getBoundingClientRect();
    const ratio = Math.min(window.devicePixelRatio || 1, 2);
    width = Math.max(1, Math.round(bounds.width));
    height = Math.max(1, Math.round(bounds.height));
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
    context.setTransform(ratio, 0, 0, ratio, 0, 0);

    const targetCount = Math.max(18, Math.min(48, Math.round((width * height) / 34000)));
    nodes = Array.from({ length: targetCount }, (_, index) => ({
      x: ((index * 83) % width) + Math.random() * 44,
      y: ((index * 137) % height) + Math.random() * 44,
      vx: 0.025 + Math.random() * 0.055,
      vy: -0.018 - Math.random() * 0.04,
      phase: Math.random() * Math.PI * 2,
    }));
  };

  const drawPerspectiveGrid = (time: number) => {
    const horizon = height * 0.42;
    const vanishingX = width * 0.38;
    context.save();
    context.strokeStyle = 'rgba(52, 226, 218, 0.105)';
    context.lineWidth = 1;

    for (let x = -width; x <= width * 2; x += 92) {
      context.beginPath();
      context.moveTo(vanishingX, horizon);
      context.lineTo(x, height + 40);
      context.stroke();
    }

    const travel = motionQuery.matches ? 0 : (time * 0.028) % 52;
    for (let index = 0; index < 18; index += 1) {
      const progress = (index * 52 + travel) / Math.max(1, height - horizon);
      const curved = Math.pow(progress, 1.72);
      const y = horizon + curved * (height - horizon);
      if (y > horizon && y < height + 2) {
        context.globalAlpha = Math.min(0.82, 0.18 + curved * 0.72);
        context.beginPath();
        context.moveTo(0, y);
        context.lineTo(width, y);
        context.stroke();
      }
    }
    context.restore();
  };

  const drawNodes = (time: number) => {
    if (!motionQuery.matches) {
      nodes.forEach((node) => {
        node.x += node.vx;
        node.y += node.vy;
        if (node.x > width + 18) node.x = -18;
        if (node.y < -18) node.y = height + 18;
      });
    }

    context.save();
    for (let left = 0; left < nodes.length; left += 1) {
      const first = nodes[left];
      if (!first) continue;

      for (let right = left + 1; right < nodes.length; right += 1) {
        const second = nodes[right];
        if (!second) continue;

        const dx = first.x - second.x;
        const dy = first.y - second.y;
        const distance = Math.sqrt(dx * dx + dy * dy);
        if (distance < 126) {
          context.strokeStyle = `rgba(61, 223, 215, ${0.12 * (1 - distance / 126)})`;
          context.beginPath();
          context.moveTo(first.x, first.y);
          context.lineTo(second.x, second.y);
          context.stroke();
        }
      }

      const pulse = motionQuery.matches ? 0.72 : 0.54 + Math.sin(time * 0.0014 + first.phase) * 0.22;
      context.fillStyle = `rgba(142, 255, 249, ${pulse})`;
      context.fillRect(first.x - 1, first.y - 1, 2, 2);
    }
    context.restore();
  };

  const drawScanner = (time: number) => {
    const scanY = motionQuery.matches ? height * 0.65 : ((time * 0.052) % (height + 220)) - 110;
    context.save();
    context.strokeStyle = 'rgba(101, 255, 246, 0.23)';
    context.lineWidth = 1;
    context.beginPath();
    context.moveTo(0, scanY);
    context.lineTo(width, scanY);
    context.stroke();
    context.fillStyle = 'rgba(64, 232, 221, 0.025)';
    context.fillRect(0, scanY - 26, width, 52);
    context.restore();
  };

  const render = (time = 0) => {
    context.clearRect(0, 0, width, height);
    drawPerspectiveGrid(time);
    drawNodes(time);
    drawScanner(time);
    if (!disposed && !motionQuery.matches) {
      animationFrame = window.requestAnimationFrame(render);
    }
  };

  const restart = () => {
    window.cancelAnimationFrame(animationFrame);
    render(performance.now());
  };

  const handleResize = () => {
    resize();
    if (motionQuery.matches) render(performance.now());
  };

  resize();
  render(performance.now());
  window.addEventListener('resize', handleResize);
  motionQuery.addEventListener('change', restart);

  return () => {
    disposed = true;
    window.cancelAnimationFrame(animationFrame);
    window.removeEventListener('resize', handleResize);
    motionQuery.removeEventListener('change', restart);
  };
};

onMounted(() => {
  stopScene = startScene(sceneCanvas.value);
});

onBeforeUnmount(() => {
  stopScene?.();
});
</script>

<template>
  <main class="login-page">
    <canvas ref="sceneCanvas" class="scene-canvas" aria-hidden="true"></canvas>

    <div class="ambient-rail ambient-rail-top" aria-hidden="true"></div>
    <div class="ambient-rail ambient-rail-bottom" aria-hidden="true"></div>

    <section class="login-layout">
      <div class="identity-zone">
        <div class="brand-lockup">
          <span class="brand-mark">
            <img src="../assets/images/icon.png" alt="DNF-Admin" />
          </span>
          <span class="brand-name">DNF-Admin</span>
        </div>

        <div class="identity-copy">
          <span class="system-code">CONTROL NODE 01</span>
          <h1>运营控制台</h1>
          <p>DNF 游戏服务管理入口</p>
        </div>

        <div class="signal-readout" aria-hidden="true">
          <span></span>
          <span></span>
          <span></span>
          <span></span>
          <span></span>
          <span></span>
        </div>
      </div>

      <div class="access-zone">
        <div class="access-index" aria-hidden="true">
          <span>AUTH</span>
          <strong>01</strong>
        </div>

        <section class="login-panel" aria-labelledby="login-title">
          <header class="panel-header">
            <div>
              <span class="panel-kicker">OPERATOR ACCESS</span>
              <h2 id="login-title">管理员登录</h2>
            </div>
            <span class="panel-status" :class="{ busy: submitting }" aria-hidden="true">
              <i></i>
            </span>
          </header>

          <a-form class="login-form" layout="vertical" :model="form" @submit="login">
            <a-form-item field="account">
              <template #label>
                <span id="login-account-label" @click="focusField('login-account')">管理员账号</span>
              </template>
              <a-input
                v-model="form.account"
                name="username"
                autocomplete="username"
                :input-attrs="{ id: 'login-account', 'aria-labelledby': 'login-account-label' }"
                placeholder="输入账号"
                allow-clear
                :disabled="submitting"
                @input="clearError"
              >
                <template #prefix>
                  <icon-user />
                </template>
              </a-input>
            </a-form-item>

            <a-form-item field="password">
              <template #label>
                <span id="login-password-label" @click="focusField('login-password')">登录密码</span>
              </template>
              <a-input-password
                v-model="form.password"
                name="password"
                autocomplete="current-password"
                :input-attrs="{ id: 'login-password', 'aria-labelledby': 'login-password-label' }"
                placeholder="输入密码"
                allow-clear
                :disabled="submitting"
                @input="clearError"
              >
                <template #prefix>
                  <icon-lock />
                </template>
              </a-input-password>
            </a-form-item>

            <p v-if="errorText" class="form-error" role="alert">{{ errorText }}</p>

            <div class="login-actions">
              <a-button class="reset-button" size="large" :disabled="submitting" @click="resetForm">
                <icon-refresh />
                重置
              </a-button>
              <a-button
                class="login-button"
                type="primary"
                size="large"
                html-type="submit"
                :loading="submitting"
              >
                进入后台
                <icon-arrow-right />
              </a-button>
            </div>
          </a-form>

          <footer class="panel-footer">
            <span class="connection-state" role="status" aria-live="polite"><i></i>{{ statusText }}</span>
            <span class="access-label">SECURE CONTROL</span>
          </footer>
        </section>
      </div>
    </section>
  </main>
</template>

<style scoped lang="less">
.login-page {
  position: relative;
  min-height: 100dvh;
  overflow: hidden;
  color: #eefeff;
  background: #05080d;
  isolation: isolate;
}

.login-page::before,
.login-page::after {
  position: absolute;
  z-index: -1;
  content: '';
  pointer-events: none;
}

.login-page::before {
  inset: 0;
  background:
    linear-gradient(90deg, rgba(5, 8, 13, 0.26) 0, rgba(5, 8, 13, 0.06) 54%, rgba(5, 8, 13, 0.5) 100%),
    repeating-linear-gradient(0deg, rgba(255, 255, 255, 0.018) 0, rgba(255, 255, 255, 0.018) 1px, transparent 1px, transparent 4px);
}

.login-page::after {
  top: 0;
  right: 0;
  width: 36%;
  height: 100%;
  border-left: 1px solid rgba(85, 238, 227, 0.08);
  background: rgba(3, 8, 13, 0.18);
}

.scene-canvas {
  position: absolute;
  z-index: -2;
  inset: 0;
  width: 100%;
  height: 100%;
}

.ambient-rail {
  position: absolute;
  left: 32px;
  right: 32px;
  height: 1px;
  background: rgba(103, 246, 236, 0.2);
  pointer-events: none;
}

.ambient-rail::before,
.ambient-rail::after {
  position: absolute;
  top: -3px;
  width: 7px;
  height: 7px;
  content: '';
  border: 1px solid rgba(103, 246, 236, 0.58);
  background: #05080d;
}

.ambient-rail::before {
  left: 0;
}

.ambient-rail::after {
  right: 0;
}

.ambient-rail-top {
  top: 28px;
}

.ambient-rail-bottom {
  bottom: 28px;
}

.login-layout {
  box-sizing: border-box;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(390px, 460px);
  gap: 80px;
  align-items: stretch;
  width: min(100%, 1540px);
  min-height: 100dvh;
  margin: 0 auto;
  padding: 64px 72px;
}

.identity-zone,
.access-zone {
  position: relative;
  display: flex;
  min-width: 0;
}

.identity-zone {
  flex-direction: column;
  justify-content: space-between;
  padding: 14px 0;
}

.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 16px;
  width: max-content;
}

.brand-mark {
  position: relative;
  display: grid;
  width: 52px;
  height: 52px;
  place-items: center;
  border: 1px solid rgba(96, 245, 234, 0.32);
  border-radius: 4px;
  background: rgba(7, 16, 23, 0.78);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08), 0 0 28px rgba(36, 214, 204, 0.12);
}

.brand-mark::before,
.brand-mark::after {
  position: absolute;
  width: 8px;
  height: 8px;
  content: '';
}

.brand-mark::before {
  top: -1px;
  left: -1px;
  border-top: 2px solid #65fff5;
  border-left: 2px solid #65fff5;
}

.brand-mark::after {
  right: -1px;
  bottom: -1px;
  border-right: 2px solid #65fff5;
  border-bottom: 2px solid #65fff5;
}

.brand-mark img {
  width: 38px;
  height: 38px;
  object-fit: contain;
}

.brand-name {
  color: #f5ffff;
  font-size: 27px;
  font-weight: 760;
  line-height: 1;
}

.identity-copy {
  max-width: 680px;
  padding: 40px 0 56px;
}

.system-code,
.panel-kicker,
.access-label {
  color: #67dcd5;
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  font-size: 11px;
  font-weight: 700;
}

.identity-copy h1 {
  margin: 18px 0 14px;
  color: #f2ffff;
  font-size: 64px;
  font-weight: 800;
  line-height: 1.08;
  text-shadow: 0 0 42px rgba(73, 238, 228, 0.14);
}

.identity-copy p {
  margin: 0;
  color: #8ca8b3;
  font-size: 18px;
  line-height: 1.7;
}

.signal-readout {
  display: grid;
  grid-template-columns: repeat(6, minmax(18px, 68px));
  gap: 8px;
  align-items: end;
  width: min(100%, 420px);
  height: 44px;
}

.signal-readout span {
  height: 8px;
  border-top: 1px solid rgba(103, 246, 236, 0.55);
  border-bottom: 1px solid rgba(103, 246, 236, 0.12);
  background: rgba(51, 219, 209, 0.07);
  animation: signal 2.8s ease-in-out infinite;
}

.signal-readout span:nth-child(2) { height: 18px; animation-delay: 0.25s; }
.signal-readout span:nth-child(3) { height: 31px; animation-delay: 0.5s; }
.signal-readout span:nth-child(4) { height: 15px; animation-delay: 0.75s; }
.signal-readout span:nth-child(5) { height: 38px; animation-delay: 1s; }
.signal-readout span:nth-child(6) { height: 23px; animation-delay: 1.25s; }

.access-zone {
  align-items: center;
  justify-content: center;
}

.access-index {
  position: absolute;
  top: 26px;
  right: 0;
  display: flex;
  align-items: baseline;
  gap: 9px;
  color: rgba(134, 238, 232, 0.5);
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
}

.access-index span {
  font-size: 10px;
}

.access-index strong {
  color: rgba(230, 255, 253, 0.74);
  font-size: 22px;
}

.login-panel {
  position: relative;
  box-sizing: border-box;
  width: 100%;
  padding: 34px;
  overflow: hidden;
  border: 1px solid rgba(102, 237, 228, 0.24);
  border-radius: 8px;
  background: rgba(7, 14, 22, 0.88);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.065),
    0 30px 80px rgba(0, 0, 0, 0.42),
    0 0 50px rgba(28, 196, 187, 0.07);
  backdrop-filter: blur(18px);
}

.login-panel::before,
.login-panel::after {
  position: absolute;
  content: '';
  pointer-events: none;
}

.login-panel::before {
  top: 0;
  left: 34px;
  width: 82px;
  height: 2px;
  background: #61f5eb;
  box-shadow: 0 0 18px rgba(97, 245, 235, 0.7);
}

.login-panel::after {
  right: 12px;
  bottom: 12px;
  width: 26px;
  height: 26px;
  border-right: 1px solid rgba(103, 246, 236, 0.42);
  border-bottom: 1px solid rgba(103, 246, 236, 0.42);
}

.panel-header,
.panel-footer,
.login-actions {
  display: flex;
  align-items: center;
}

.panel-header {
  justify-content: space-between;
  margin-bottom: 34px;
}

.panel-header h2 {
  margin: 8px 0 0;
  color: #f5ffff;
  font-size: 28px;
  font-weight: 760;
  line-height: 1.2;
}

.panel-status {
  position: relative;
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border: 1px solid rgba(104, 235, 226, 0.24);
  border-radius: 50%;
}

.panel-status::before {
  position: absolute;
  inset: 6px;
  content: '';
  border: 1px dashed rgba(104, 235, 226, 0.38);
  border-radius: 50%;
  animation: rotate 8s linear infinite;
}

.panel-status i,
.connection-state i {
  display: inline-block;
  border-radius: 50%;
  background: #5ff4b7;
  box-shadow: 0 0 12px rgba(95, 244, 183, 0.68);
}

.panel-status i {
  width: 7px;
  height: 7px;
}

.panel-status.busy i {
  background: #64f5eb;
  box-shadow: 0 0 14px rgba(100, 245, 235, 0.8);
}

.login-form :deep(.arco-form-item) {
  margin-bottom: 22px;
}

.login-form :deep(.arco-form-item-label-col) {
  padding-bottom: 8px;
}

.login-form :deep(.arco-form-item-label) {
  color: #a9c3cb;
  font-size: 13px;
  font-weight: 600;
}

.login-form :deep(.arco-form-item-label span) {
  cursor: pointer;
}

.login-form :deep(.arco-input-wrapper) {
  height: 48px;
  padding: 0 14px;
  border: 1px solid rgba(133, 180, 193, 0.24);
  border-radius: 4px;
  color: #efffff;
  background: rgba(4, 10, 17, 0.86);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.025);
  transition: border-color 160ms ease, box-shadow 160ms ease, background-color 160ms ease;
}

.login-form :deep(.arco-input-wrapper:hover) {
  border-color: rgba(99, 225, 216, 0.46);
}

.login-form :deep(.arco-input-wrapper.arco-input-focus) {
  border-color: #5ce9df;
  background: rgba(6, 17, 24, 0.96);
  box-shadow: 0 0 0 3px rgba(58, 215, 205, 0.12), inset 3px 0 0 #5ce9df;
}

.login-form :deep(.arco-input),
.login-form :deep(.arco-input-prefix),
.login-form :deep(.arco-input-suffix) {
  color: #efffff;
}

.login-form :deep(.arco-input::placeholder) {
  color: #627982;
}

.login-form :deep(.arco-input-clear-btn),
.login-form :deep(.arco-input-password-visibility-btn) {
  color: #79969f;
}

.form-error {
  min-height: 20px;
  margin: -9px 0 18px;
  color: #ff7b88;
  font-size: 12px;
  line-height: 1.5;
}

.login-actions {
  gap: 12px;
  margin-top: 6px;
}

.login-actions :deep(.arco-btn) {
  height: 46px;
  border-radius: 4px;
  font-weight: 700;
  transition: transform 120ms ease, border-color 160ms ease, background-color 160ms ease, box-shadow 160ms ease;
}

.login-actions :deep(.arco-btn:active) {
  transform: translateY(1px) scale(0.99);
}

.reset-button {
  flex: 0 0 112px;
  border-color: rgba(137, 183, 193, 0.26) !important;
  color: #a9c3cb !important;
  background: rgba(13, 24, 32, 0.72) !important;
}

.reset-button:hover {
  border-color: rgba(99, 225, 216, 0.54) !important;
  color: #eaffff !important;
  background: rgba(18, 38, 46, 0.78) !important;
}

.login-button {
  flex: 1;
  border-color: #53ded5 !important;
  color: #031110 !important;
  background: #5ce9df !important;
  box-shadow: 0 10px 28px rgba(50, 218, 208, 0.18);
}

.login-button:hover {
  border-color: #8bfff7 !important;
  background: #78f7ee !important;
  box-shadow: 0 12px 32px rgba(50, 218, 208, 0.28);
}

.panel-footer {
  justify-content: space-between;
  gap: 18px;
  margin-top: 30px;
  padding-top: 18px;
  border-top: 1px solid rgba(120, 172, 184, 0.13);
  color: #78919a;
  font-size: 11px;
}

.connection-state {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.connection-state i {
  width: 6px;
  height: 6px;
}

@keyframes signal {
  0%, 100% { opacity: 0.45; transform: scaleY(0.72); }
  50% { opacity: 1; transform: scaleY(1); }
}

@keyframes rotate {
  to { transform: rotate(360deg); }
}

@media (max-width: 980px) {
  .login-layout {
    grid-template-columns: minmax(0, 1fr) minmax(360px, 430px);
    gap: 40px;
    padding: 58px 42px;
  }

  .identity-copy h1 {
    font-size: 52px;
  }
}

@media (max-width: 860px) {
  .login-page::after {
    display: none;
  }

  .ambient-rail {
    right: 16px;
    left: 16px;
  }

  .ambient-rail-top { top: 16px; }
  .ambient-rail-bottom { bottom: 16px; }

  .login-layout {
    display: flex;
    flex-direction: column;
    justify-content: center;
    min-height: 100dvh;
    padding: 74px 18px 54px;
  }

  .identity-zone {
    position: absolute;
    top: 34px;
    left: 24px;
    padding: 0;
  }

  .brand-lockup {
    gap: 10px;
  }

  .brand-mark {
    width: 38px;
    height: 38px;
  }

  .brand-mark img {
    width: 29px;
    height: 29px;
  }

  .brand-name {
    font-size: 20px;
  }

  .identity-copy,
  .signal-readout,
  .access-index {
    display: none;
  }

  .access-zone {
    width: 100%;
  }

  .login-panel {
    width: min(100%, 430px);
    margin: 0 auto;
    padding: 26px 22px;
    backdrop-filter: blur(14px);
  }

  .panel-header {
    margin-bottom: 26px;
  }

  .panel-header h2 {
    font-size: 24px;
  }

  .panel-status {
    width: 38px;
    height: 38px;
  }

  .login-actions {
    align-items: stretch;
  }

  .reset-button {
    flex-basis: 98px;
  }

  .panel-footer {
    margin-top: 24px;
  }
}

@media (max-width: 380px) {
  .login-layout {
    padding-right: 12px;
    padding-left: 12px;
  }

  .login-panel {
    padding: 24px 18px;
  }

  .access-label {
    display: none;
  }
}

@media (max-width: 860px) and (max-height: 680px) {
  .login-layout {
    justify-content: flex-start;
    padding-top: 88px;
    padding-bottom: 32px;
  }
}

@media (max-height: 680px) and (min-width: 861px) {
  .login-layout {
    padding-top: 40px;
    padding-bottom: 40px;
  }

  .identity-copy {
    padding-bottom: 30px;
  }

  .identity-copy h1 {
    font-size: 48px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .signal-readout span,
  .panel-status::before {
    animation: none;
  }

  .login-actions :deep(.arco-btn) {
    transition: none;
  }
}

@media (prefers-reduced-transparency: reduce) {
  .login-panel {
    background: #09121b;
    backdrop-filter: none;
  }
}
</style>
