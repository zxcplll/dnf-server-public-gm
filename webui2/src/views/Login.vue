<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import Request from '../api/Request.ts';
import router from '../router';

interface SceneNode {
  x: number;
  y: number;
  vx: number;
  vy: number;
  phase: number;
}

type PreviewTheme = 'midnight' | 'quantum' | 'aurora';
type SceneEffect = 'network' | 'matrix' | 'gravity' | 'wave';

interface EffectMeta {
  key: SceneEffect;
  index: string;
  name: string;
  caption: string;
}

interface ThemeMeta {
  key: PreviewTheme;
  index: string;
  name: string;
  systemCode: string;
  headline: string;
  subtitle: string;
  panelKicker: string;
  accessLabel: string;
}

interface ScenePalette {
  grid: string;
  connection: [number, number, number];
  node: [number, number, number];
  scanner: string;
  scannerFill: string;
  signature: string;
  horizon: number;
  vanishingX: number;
}

interface MatrixColumn {
  x: number;
  y: number;
  speed: number;
  length: number;
  phase: number;
}

const themeOptions: ThemeMeta[] = [
  {
    key: 'midnight',
    index: '01',
    name: '深夜控制台',
    systemCode: 'CONTROL NODE 01',
    headline: '运营控制台',
    subtitle: 'DNF 游戏服务管理入口',
    panelKicker: 'OPERATOR ACCESS',
    accessLabel: 'SECURE CONTROL',
  },
  {
    key: 'quantum',
    index: '02',
    name: '量子导航舱',
    systemCode: 'QUANTUM LINK 02',
    headline: '量子指挥舱',
    subtitle: '跨节点运行状态与身份接入',
    panelKicker: 'NAVIGATION AUTH',
    accessLabel: 'VECTOR LOCK',
  },
  {
    key: 'aurora',
    index: '03',
    name: '极光作业台',
    systemCode: 'AURORA OPS 03',
    headline: '极光作业台',
    subtitle: '实时运维流与权限校验入口',
    panelKicker: 'SIGNAL ACCESS',
    accessLabel: 'CHANNEL READY',
  },
];

const themeByKey = Object.fromEntries(themeOptions.map((option) => [option.key, option])) as Record<PreviewTheme, ThemeMeta>;
const effectOptions: EffectMeta[] = [
  { key: 'network', index: 'A', name: '星空连线', caption: 'NETWORK FIELD' },
  { key: 'matrix', index: 'B', name: '数据雨', caption: 'MATRIX STREAM' },
  { key: 'gravity', index: 'C', name: '引力井', caption: 'GRAVITY WELL' },
  { key: 'wave', index: 'D', name: '波浪扫描', caption: 'WAVE SCAN' },
];
const effectByKey = Object.fromEntries(effectOptions.map((option) => [option.key, option])) as Record<SceneEffect, EffectMeta>;
const scenePalettes: Record<PreviewTheme, ScenePalette> = {
  midnight: {
    grid: 'rgba(52, 226, 218, 0.105)',
    connection: [61, 223, 215],
    node: [142, 255, 249],
    scanner: 'rgba(101, 255, 246, 0.23)',
    scannerFill: 'rgba(64, 232, 221, 0.025)',
    signature: 'rgba(103, 246, 236, 0.12)',
    horizon: 0.42,
    vanishingX: 0.38,
  },
  quantum: {
    grid: 'rgba(88, 153, 255, 0.12)',
    connection: [88, 153, 255],
    node: [164, 206, 255],
    scanner: 'rgba(255, 180, 84, 0.28)',
    scannerFill: 'rgba(255, 180, 84, 0.025)',
    signature: 'rgba(115, 173, 255, 0.18)',
    horizon: 0.34,
    vanishingX: 0.57,
  },
  aurora: {
    grid: 'rgba(92, 221, 167, 0.105)',
    connection: [92, 221, 167],
    node: [177, 255, 219],
    scanner: 'rgba(255, 138, 114, 0.26)',
    scannerFill: 'rgba(255, 138, 114, 0.024)',
    signature: 'rgba(124, 241, 191, 0.16)',
    horizon: 0.52,
    vanishingX: 0.2,
  },
};

const searchParams = new URLSearchParams(window.location.search);
const previewEnabled = searchParams.get('preview') === '1';
const requestedTheme = searchParams.get('theme') as PreviewTheme | null;
const requestedEffect = searchParams.get('effect') as SceneEffect | null;
const previewTheme = ref<PreviewTheme>(
  previewEnabled && requestedTheme && Object.prototype.hasOwnProperty.call(themeByKey, requestedTheme)
    ? requestedTheme
    : 'aurora',
);
const sceneEffect = ref<SceneEffect>(
  previewEnabled && requestedEffect && Object.prototype.hasOwnProperty.call(effectByKey, requestedEffect)
    ? requestedEffect
    : 'matrix',
);
const activeTheme = computed(() => themeByKey[previewTheme.value]);
const activeEffect = computed(() => effectByKey[sceneEffect.value]);

const tickerRows = [
  'AUTH.OK  /  UID.SCAN  /  PVF.CACHE  /  GATEWAY.27043  /  FRIDA.ONLINE',
  '角色在线  /  邮件队列  /  公会同步  /  资源监控  /  SESSION.READY',
  'DB.SNAPSHOT  /  ITEM.INDEX  /  TASK.RUNNER  /  ACCESS.GRANTED  /  NODE.03',
  'DNF-ADMIN  /  CONTROL BUS  /  STATUS.LIVE  /  PACKET.LINK  /  OPS.MODE',
];

const form = reactive({
  account: '',
  password: '',
});

const sceneCanvas = ref<HTMLCanvasElement | null>(null);
const submitting = ref(false);
const statusText = ref('等待身份验证');
const errorText = ref('');
const pointerStyle = reactive<Record<string, string>>({
  '--pointer-x': '50%',
  '--pointer-y': '50%',
  '--panel-tilt-x': '0deg',
  '--panel-tilt-y': '0deg',
});

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

const selectPreviewTheme = (theme: PreviewTheme) => {
  previewTheme.value = theme;
  if (!previewEnabled) return;

  const nextUrl = new URL(window.location.href);
  nextUrl.searchParams.set('preview', '1');
  nextUrl.searchParams.set('theme', theme);
  window.history.replaceState({}, '', nextUrl);
  window.dispatchEvent(new Event('resize'));
};

const selectPreviewEffect = (effect: SceneEffect) => {
  sceneEffect.value = effect;
  if (!previewEnabled) return;

  const nextUrl = new URL(window.location.href);
  nextUrl.searchParams.set('preview', '1');
  nextUrl.searchParams.set('effect', effect);
  nextUrl.searchParams.set('theme', previewTheme.value);
  window.history.replaceState({}, '', nextUrl);
  window.dispatchEvent(new Event('resize'));
};

const updatePointerEffect = (event: PointerEvent) => {
  const target = event.currentTarget as HTMLElement | null;
  if (!target || event.pointerType === 'touch') return;
  const bounds = target.getBoundingClientRect();
  const x = Math.min(100, Math.max(0, ((event.clientX - bounds.left) / bounds.width) * 100));
  const y = Math.min(100, Math.max(0, ((event.clientY - bounds.top) / bounds.height) * 100));
  pointerStyle['--pointer-x'] = `${x}%`;
  pointerStyle['--pointer-y'] = `${y}%`;
  pointerStyle['--panel-tilt-x'] = `${(50 - y) / 24}deg`;
  pointerStyle['--panel-tilt-y'] = `${(x - 50) / 28}deg`;
};

const resetPointerEffect = () => {
  pointerStyle['--pointer-x'] = '50%';
  pointerStyle['--pointer-y'] = '50%';
  pointerStyle['--panel-tilt-x'] = '0deg';
  pointerStyle['--panel-tilt-y'] = '0deg';
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
  let matrixColumns: MatrixColumn[] = [];
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

    const matrixCount = Math.max(18, Math.min(72, Math.round(width / 24)));
    matrixColumns = Array.from({ length: matrixCount }, (_, index) => ({
      x: (index + 0.5) * (width / matrixCount),
      y: Math.random() * height,
      speed: 0.025 + Math.random() * 0.045,
      length: 7 + Math.round(Math.random() * 14),
      phase: Math.random() * Math.PI * 2,
    }));
  };

  const drawMatrixRain = (time: number) => {
    const palette = scenePalettes[previewTheme.value];
    const glyphs = '01<>[]{}\\/+*#X';
    const step = 15;
    context.save();
    context.font = '11px JetBrains Mono, Cascadia Code, Consolas, monospace';
    context.textAlign = 'center';
    matrixColumns.forEach((column) => {
      const travel = motionQuery.matches ? 0 : time * column.speed;
      const head = (column.y + travel) % (height + column.length * step);
      for (let index = 0; index < column.length; index += 1) {
        const y = head - index * step;
        if (y < -step || y > height + step) continue;
        const alpha = Math.max(0.08, 0.74 - index / (column.length + 1));
        context.fillStyle = index === 0
          ? `rgba(${palette.node.join(', ')}, 0.92)`
          : `rgba(${palette.connection.join(', ')}, ${alpha * 0.52})`;
        const glyphIndex = Math.floor(index + column.phase * 3 + (motionQuery.matches ? 0 : time * 0.004)) % glyphs.length;
        context.fillText(glyphs[glyphIndex] || '0', column.x, y);
      }
    });
    context.restore();
  };

  const drawGravityWell = (time: number) => {
    const palette = scenePalettes[previewTheme.value];
    const centerX = width * (previewTheme.value === 'aurora' ? 0.28 : 0.7);
    const centerY = height * 0.46;
    const drift = motionQuery.matches ? 0 : time * 0.00055;
    context.save();

    const glow = context.createRadialGradient(centerX, centerY, 6, centerX, centerY, Math.max(width, height) * 0.38);
    glow.addColorStop(0, 'rgba(3, 7, 11, 0.98)');
    glow.addColorStop(0.18, `rgba(${palette.connection.join(', ')}, 0.1)`);
    glow.addColorStop(1, 'rgba(3, 7, 11, 0)');
    context.fillStyle = glow;
    context.fillRect(0, 0, width, height);

    context.strokeStyle = `rgba(${palette.connection.join(', ')}, 0.23)`;
    context.lineWidth = 1;
    for (let ring = 0; ring < 5; ring += 1) {
      context.beginPath();
      context.ellipse(
        centerX,
        centerY,
        36 + ring * 31,
        10 + ring * 13,
        drift + ring * 0.16,
        0,
        Math.PI * 2,
      );
      context.stroke();
    }

    nodes.slice(0, 34).forEach((node, index) => {
      const radius = 56 + (index * 23) % Math.max(80, Math.min(width, height) * 0.6);
      const angle = drift * (1 + (index % 3) * 0.24) + node.phase + index * 0.21;
      const x = centerX + Math.cos(angle) * radius;
      const y = centerY + Math.sin(angle) * radius * 0.36;
      context.fillStyle = `rgba(${palette.node.join(', ')}, ${0.24 + (index % 5) * 0.08})`;
      context.fillRect(x, y, index % 4 === 0 ? 3 : 2, index % 4 === 0 ? 3 : 2);
    });

    context.fillStyle = '#020407';
    context.beginPath();
    context.arc(centerX, centerY, 18, 0, Math.PI * 2);
    context.fill();
    context.strokeStyle = `rgba(${palette.node.join(', ')}, 0.84)`;
    context.beginPath();
    context.arc(centerX, centerY, 22, 0, Math.PI * 2);
    context.stroke();
    context.restore();
  };

  const drawWaveField = (time: number) => {
    const palette = scenePalettes[previewTheme.value];
    const phase = motionQuery.matches ? 0 : time * 0.00055;
    context.save();
    context.lineWidth = 1;
    for (let band = 0; band < 14; band += 1) {
      const baseline = height * 0.2 + band * (height * 0.062);
      const amplitude = 8 + band * 0.8;
      context.strokeStyle = `rgba(${palette.connection.join(', ')}, ${0.06 + (band % 4) * 0.025})`;
      context.beginPath();
      for (let x = 0; x <= width; x += 10) {
        const y = baseline
          + Math.sin(x * 0.012 + phase + band * 0.58) * amplitude
          + Math.sin(x * 0.027 - phase * 0.7 + band) * (amplitude * 0.3);
        if (x === 0) context.moveTo(x, y);
        else context.lineTo(x, y);
      }
      context.stroke();
    }

    const scanX = motionQuery.matches ? width * 0.52 : ((time * 0.09) % (width + 260)) - 130;
    context.strokeStyle = palette.scanner;
    context.fillStyle = palette.scannerFill;
    context.fillRect(scanX - 48, 0, 96, height);
    context.beginPath();
    context.moveTo(scanX, 0);
    context.lineTo(scanX, height);
    context.stroke();
    context.restore();
  };

  const drawPerspectiveGrid = (time: number) => {
    const palette = scenePalettes[previewTheme.value];
    const horizon = height * palette.horizon;
    const vanishingX = width * palette.vanishingX;
    context.save();
    context.strokeStyle = palette.grid;
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

  const drawThemeSignature = (time: number) => {
    const theme = previewTheme.value;
    if (theme === 'midnight') return;

    const palette = scenePalettes[theme];
    context.save();
    context.strokeStyle = palette.signature;
    context.lineWidth = 1;

    if (theme === 'quantum') {
      const centerX = width * 0.72;
      const centerY = height * 0.34;
      const drift = motionQuery.matches ? 0 : time * 0.00008;
      for (let index = 0; index < 3; index += 1) {
        context.beginPath();
        context.ellipse(
          centerX,
          centerY,
          120 + index * 48,
          28 + index * 16,
          drift + index * 0.52,
          0,
          Math.PI * 2,
        );
        context.stroke();
      }
      context.fillStyle = 'rgba(255, 180, 84, 0.64)';
      context.fillRect(centerX - 2, centerY - 2, 4, 4);
    } else {
      for (let band = 0; band < 3; band += 1) {
        const baseline = height * (0.56 + band * 0.09);
        context.beginPath();
        for (let x = 0; x <= width; x += 12) {
          const phase = motionQuery.matches ? 0 : time * 0.0007;
          const y = baseline + Math.sin(x * 0.018 + phase + band * 1.7) * (8 + band * 2);
          if (x === 0) context.moveTo(x, y);
          else context.lineTo(x, y);
        }
        context.stroke();
      }
    }
    context.restore();
  };

  const drawNodes = (time: number) => {
    const palette = scenePalettes[previewTheme.value];
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
          context.strokeStyle = `rgba(${palette.connection.join(', ')}, ${0.12 * (1 - distance / 126)})`;
          context.beginPath();
          context.moveTo(first.x, first.y);
          context.lineTo(second.x, second.y);
          context.stroke();
        }
      }

      const pulse = motionQuery.matches ? 0.72 : 0.54 + Math.sin(time * 0.0014 + first.phase) * 0.22;
      context.fillStyle = `rgba(${palette.node.join(', ')}, ${pulse})`;
      context.fillRect(first.x - 1, first.y - 1, 2, 2);
    }
    context.restore();
  };

  const drawScanner = (time: number) => {
    const palette = scenePalettes[previewTheme.value];
    const scanY = motionQuery.matches ? height * 0.65 : ((time * 0.052) % (height + 220)) - 110;
    context.save();
    context.strokeStyle = palette.scanner;
    context.lineWidth = 1;
    context.beginPath();
    context.moveTo(0, scanY);
    context.lineTo(width, scanY);
    context.stroke();
    context.fillStyle = palette.scannerFill;
    context.fillRect(0, scanY - 26, width, 52);
    context.restore();
  };

  const render = (time = 0) => {
    context.clearRect(0, 0, width, height);
    if (sceneEffect.value === 'matrix') {
      drawPerspectiveGrid(time);
      drawMatrixRain(time);
      drawThemeSignature(time);
      drawScanner(time);
    } else if (sceneEffect.value === 'gravity') {
      drawGravityWell(time);
      drawScanner(time);
    } else if (sceneEffect.value === 'wave') {
      drawPerspectiveGrid(time);
      drawWaveField(time);
      drawThemeSignature(time);
    } else {
      drawPerspectiveGrid(time);
      drawThemeSignature(time);
      drawNodes(time);
      drawScanner(time);
    }
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
  <main
    class="login-page"
    :class="[`theme-${previewTheme}`, { 'preview-active': previewEnabled }]"
    :style="pointerStyle"
    @pointermove="updatePointerEffect"
    @pointerleave="resetPointerEffect"
  >
    <canvas ref="sceneCanvas" class="scene-canvas" aria-hidden="true"></canvas>

    <div class="ticker-field" aria-hidden="true">
      <div
        v-for="(row, index) in tickerRows"
        :key="row"
        class="ticker-row"
        :class="`ticker-row-${index + 1}`"
      >
        <span v-for="copy in 2" :key="copy">{{ row }}</span>
      </div>
    </div>

    <div class="cursor-reticle" aria-hidden="true"></div>

    <div class="ambient-rail ambient-rail-top" aria-hidden="true"></div>
    <div class="ambient-rail ambient-rail-bottom" aria-hidden="true"></div>

    <nav v-if="previewEnabled" class="preview-switcher" aria-label="登录页方案预览">
      <span class="preview-switcher-label">LOGIN VIEW</span>
      <div class="preview-options" role="group" aria-label="切换登录页方案">
        <button
          v-for="option in themeOptions"
          :key="option.key"
          type="button"
          :class="{ active: previewTheme === option.key }"
          :aria-pressed="previewTheme === option.key"
          @click="selectPreviewTheme(option.key)"
        >
          <span>{{ option.index }}</span>
          {{ option.name }}
        </button>
      </div>
    </nav>

    <nav v-if="previewEnabled" class="scene-effect-switcher" aria-label="Scene effects">
      <span class="scene-effect-label">SCENE LAB / {{ activeEffect.caption }}</span>
      <div class="scene-effect-options" role="group" aria-label="Select scene effect">
        <button
          v-for="option in effectOptions"
          :key="option.key"
          type="button"
          :class="{ active: sceneEffect === option.key }"
          :aria-pressed="sceneEffect === option.key"
          :title="option.caption"
          @click="selectPreviewEffect(option.key)"
        >
          <span>{{ option.index }}</span>
          {{ option.name }}
        </button>
      </div>
    </nav>

    <section class="login-layout">
      <div class="identity-zone">
        <div class="brand-lockup">
          <span class="brand-mark">
            <img src="../assets/images/icon.png" alt="DNF-Admin" />
          </span>
          <span class="brand-name">DNF-Admin</span>
        </div>

        <div class="identity-copy">
          <span class="system-code">{{ activeTheme.systemCode }}</span>
          <h1>{{ activeTheme.headline }}</h1>
          <p>{{ activeTheme.subtitle }}</p>
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
          <strong>{{ activeTheme.index }}</strong>
        </div>

        <section class="login-panel" aria-labelledby="login-title">
          <header class="panel-header">
            <div>
              <span class="panel-kicker">{{ activeTheme.panelKicker }}</span>
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
            <span class="access-label">{{ activeTheme.accessLabel }}</span>
          </footer>
        </section>
      </div>
    </section>
  </main>
</template>

<style scoped lang="less">
.login-page {
  --preview-accent: #61f5eb;
  --preview-accent-soft: rgba(97, 245, 235, 0.24);
  --preview-secondary: #5ff4b7;
  --preview-panel: rgba(7, 14, 22, 0.88);
  position: relative;
  min-height: 100dvh;
  overflow: hidden;
  color: #eefeff;
  background: #05080d;
  isolation: isolate;
}

.login-page.theme-quantum {
  --preview-accent: #78aefc;
  --preview-accent-soft: rgba(120, 174, 252, 0.28);
  --preview-secondary: #ffb454;
  --preview-panel: rgba(7, 15, 28, 0.9);
  background: #050b15;
}

.login-page.theme-aurora {
  --preview-accent: #7cf1bf;
  --preview-accent-soft: rgba(124, 241, 191, 0.26);
  --preview-secondary: #ff8a72;
  --preview-panel: rgba(7, 18, 15, 0.9);
  background: #060f0d;
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

.ticker-field {
  position: absolute;
  z-index: -1;
  inset: 0;
  overflow: hidden;
  color: var(--preview-accent);
  opacity: 0.82;
  pointer-events: none;
  mask-image: linear-gradient(90deg, transparent, #000 12%, #000 88%, transparent);
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 12%, #000 88%, transparent);
}

.ticker-row {
  position: absolute;
  left: -25%;
  display: flex;
  gap: 56px;
  width: max-content;
  color: currentColor;
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  line-height: 1;
  opacity: 0.16;
  white-space: nowrap;
  animation: ticker-left 34s linear infinite;
}

.ticker-row span {
  flex: 0 0 auto;
}

.ticker-row-1 { top: 18%; animation-duration: 32s; }
.ticker-row-2 { top: 36%; opacity: 0.12; animation: ticker-right 42s linear infinite; }
.ticker-row-3 { top: 64%; opacity: 0.14; animation-duration: 38s; }
.ticker-row-4 { top: 82%; opacity: 0.1; animation: ticker-right 48s linear infinite; }

.cursor-reticle {
  position: absolute;
  z-index: 1;
  top: var(--pointer-y);
  left: var(--pointer-x);
  width: 48px;
  height: 48px;
  border: 1px solid var(--preview-accent-soft);
  border-radius: 2px;
  box-shadow: inset 0 0 0 7px rgba(5, 10, 16, 0.06), 0 0 22px var(--preview-accent-soft);
  opacity: 0.58;
  pointer-events: none;
  transform: translate(-50%, -50%);
  transition: top 100ms ease-out, left 100ms ease-out;
}

.cursor-reticle::before,
.cursor-reticle::after {
  position: absolute;
  content: '';
  background: var(--preview-accent);
  opacity: 0.72;
}

.cursor-reticle::before {
  top: -12px;
  bottom: -12px;
  left: 50%;
  width: 1px;
}

.cursor-reticle::after {
  top: 50%;
  right: -12px;
  left: -12px;
  height: 1px;
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

.preview-switcher {
  position: absolute;
  z-index: 5;
  top: 20px;
  left: 50%;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  height: 48px;
  padding: 5px;
  border: 1px solid rgba(126, 164, 176, 0.22);
  border-radius: 5px;
  background: rgba(5, 10, 16, 0.92);
  box-shadow: 0 16px 36px rgba(0, 0, 0, 0.28), inset 0 1px 0 rgba(255, 255, 255, 0.05);
  transform: translateX(-50%);
  backdrop-filter: blur(14px);
}

.preview-switcher-label {
  padding: 0 13px 0 10px;
  color: #718a95;
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  font-size: 9px;
  font-weight: 700;
  white-space: nowrap;
}

.preview-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(108px, 1fr));
  gap: 3px;
  height: 36px;
}

.preview-options button {
  box-sizing: border-box;
  height: 36px;
  padding: 0 10px;
  overflow: hidden;
  border: 1px solid transparent;
  border-radius: 3px;
  color: #91a6af;
  font: inherit;
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0;
  white-space: nowrap;
  text-overflow: ellipsis;
  cursor: pointer;
  background: transparent;
  transition: color 160ms ease, border-color 160ms ease, background-color 160ms ease;
}

.preview-options button span {
  margin-right: 5px;
  color: #607781;
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  font-size: 9px;
}

.preview-options button:hover {
  color: #e9ffff;
  border-color: rgba(142, 185, 197, 0.24);
  background: rgba(139, 184, 196, 0.07);
}

.preview-options button.active {
  color: #f5ffff;
  border-color: var(--preview-accent-soft);
  background: rgba(138, 184, 196, 0.12);
  box-shadow: inset 0 -2px 0 var(--preview-accent);
}

.preview-options button.active span {
  color: var(--preview-secondary);
}

.scene-effect-switcher {
  position: absolute;
  z-index: 5;
  top: 76px;
  left: 50%;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  height: 40px;
  padding: 4px;
  border: 1px solid rgba(126, 164, 176, 0.18);
  border-radius: 4px;
  background: rgba(5, 10, 16, 0.82);
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.2);
  transform: translateX(-50%);
  backdrop-filter: blur(12px);
}

.scene-effect-label {
  padding: 0 10px 0 8px;
  color: #607781;
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  font-size: 9px;
  font-weight: 700;
  white-space: nowrap;
}

.scene-effect-options {
  display: grid;
  grid-template-columns: repeat(4, minmax(86px, 1fr));
  gap: 3px;
  height: 30px;
}

.scene-effect-options button {
  box-sizing: border-box;
  height: 30px;
  padding: 0 9px;
  overflow: hidden;
  border: 1px solid transparent;
  border-radius: 2px;
  color: #8299a4;
  font: inherit;
  font-size: 11px;
  font-weight: 650;
  letter-spacing: 0;
  white-space: nowrap;
  text-overflow: ellipsis;
  cursor: pointer;
  background: transparent;
  transition: color 160ms ease, border-color 160ms ease, background-color 160ms ease;
}

.scene-effect-options button span {
  margin-right: 4px;
  color: #5e7581;
  font-family: 'JetBrains Mono', 'Cascadia Code', Consolas, monospace;
  font-size: 9px;
}

.scene-effect-options button:hover,
.scene-effect-options button.active {
  color: #f2ffff;
  border-color: var(--preview-accent-soft);
  background: rgba(118, 187, 194, 0.1);
}

.scene-effect-options button.active {
  box-shadow: inset 0 -2px 0 var(--preview-accent);
}

.login-layout {
  position: relative;
  z-index: 2;
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
  transform: perspective(1200px) rotateX(var(--panel-tilt-x)) rotateY(var(--panel-tilt-y));
  transform-style: preserve-3d;
  transition: transform 140ms ease-out;
  will-change: transform;
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

.theme-quantum::before {
  background:
    linear-gradient(90deg, rgba(5, 11, 21, 0.2) 0, rgba(5, 11, 21, 0.02) 54%, rgba(5, 11, 21, 0.56) 100%),
    repeating-linear-gradient(90deg, rgba(114, 170, 255, 0.022) 0, rgba(114, 170, 255, 0.022) 1px, transparent 1px, transparent 54px);
}

.theme-quantum::after {
  width: 42%;
  border-left-color: rgba(120, 174, 252, 0.14);
  background: rgba(5, 12, 24, 0.26);
}

.theme-quantum .ambient-rail {
  background: rgba(120, 174, 252, 0.28);
}

.theme-quantum .ambient-rail::before,
.theme-quantum .ambient-rail::after {
  border-color: rgba(120, 174, 252, 0.62);
  background: #050b15;
}

.theme-quantum .brand-mark {
  border-color: rgba(120, 174, 252, 0.42);
  border-radius: 2px;
  background: rgba(7, 16, 31, 0.84);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.07), 0 0 28px rgba(69, 134, 239, 0.15);
}

.theme-quantum .brand-mark::before {
  border-color: #78aefc;
}

.theme-quantum .brand-mark::after {
  border-color: #ffb454;
}

.theme-quantum .system-code,
.theme-quantum .panel-kicker {
  color: #78aefc;
}

.theme-quantum .access-label,
.theme-quantum .access-index {
  color: #ffb454;
}

.theme-quantum .identity-copy h1 {
  text-shadow: 0 0 42px rgba(78, 142, 244, 0.18);
}

.theme-quantum .signal-readout span {
  border-color: rgba(120, 174, 252, 0.48);
  background: rgba(72, 134, 233, 0.08);
}

.theme-quantum .signal-readout span:nth-child(2),
.theme-quantum .signal-readout span:nth-child(5) {
  border-top-color: rgba(255, 180, 84, 0.62);
}

.theme-quantum .login-panel {
  border-color: rgba(120, 174, 252, 0.28);
  border-radius: 3px;
  background: var(--preview-panel);
  box-shadow:
    inset 4px 0 0 rgba(120, 174, 252, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.055),
    0 30px 80px rgba(0, 0, 0, 0.44),
    0 0 50px rgba(67, 127, 222, 0.08);
}

.theme-quantum .login-panel::before {
  width: 126px;
  background: #78aefc;
  box-shadow: 0 0 18px rgba(120, 174, 252, 0.58);
}

.theme-quantum .login-panel::after {
  border-color: rgba(255, 180, 84, 0.62);
}

.theme-quantum .panel-status {
  border-color: rgba(120, 174, 252, 0.32);
}

.theme-quantum .panel-status::before {
  border-color: rgba(120, 174, 252, 0.48);
}

.theme-quantum .panel-status i {
  background: #ffb454;
  box-shadow: 0 0 12px rgba(255, 180, 84, 0.62);
}

.theme-quantum .login-form :deep(.arco-input-wrapper:hover) {
  border-color: rgba(120, 174, 252, 0.5);
}

.theme-quantum .login-form :deep(.arco-input-wrapper.arco-input-focus) {
  border-color: #78aefc;
  background: rgba(6, 14, 27, 0.96);
  box-shadow: 0 0 0 3px rgba(94, 153, 247, 0.13), inset 3px 0 0 #78aefc;
}

.theme-quantum .reset-button:hover {
  border-color: rgba(120, 174, 252, 0.56) !important;
}

.theme-quantum .login-button {
  border-color: #ffb454 !important;
  color: #171006 !important;
  background: #ffb454 !important;
  box-shadow: 0 10px 28px rgba(255, 180, 84, 0.17);
}

.theme-quantum .login-button:hover {
  border-color: #ffd092 !important;
  background: #ffc46f !important;
  box-shadow: 0 12px 32px rgba(255, 180, 84, 0.25);
}

.theme-aurora::before {
  background:
    linear-gradient(90deg, rgba(6, 15, 13, 0.12) 0, rgba(6, 15, 13, 0.02) 52%, rgba(6, 15, 13, 0.52) 100%),
    repeating-linear-gradient(135deg, rgba(124, 241, 191, 0.018) 0, rgba(124, 241, 191, 0.018) 1px, transparent 1px, transparent 42px);
}

.theme-aurora::after {
  width: 39%;
  border-left-color: rgba(124, 241, 191, 0.12);
  background: rgba(4, 16, 13, 0.22);
}

.theme-aurora .ambient-rail {
  background: rgba(124, 241, 191, 0.25);
}

.theme-aurora .ambient-rail::before,
.theme-aurora .ambient-rail::after {
  border-color: rgba(255, 138, 114, 0.62);
  background: #060f0d;
}

.theme-aurora .brand-mark {
  border-color: rgba(124, 241, 191, 0.38);
  background: rgba(7, 22, 17, 0.82);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.07), 0 0 28px rgba(74, 211, 153, 0.13);
}

.theme-aurora .brand-mark::before {
  border-color: #7cf1bf;
}

.theme-aurora .brand-mark::after {
  border-color: #ff8a72;
}

.theme-aurora .system-code,
.theme-aurora .panel-kicker,
.theme-aurora .access-index {
  color: #7cf1bf;
}

.theme-aurora .access-label {
  color: #ff9b86;
}

.theme-aurora .identity-copy h1 {
  text-shadow: 0 0 42px rgba(84, 222, 163, 0.16);
}

.theme-aurora .signal-readout span {
  border-color: rgba(124, 241, 191, 0.48);
  background: rgba(78, 207, 151, 0.08);
}

.theme-aurora .signal-readout span:nth-child(3),
.theme-aurora .signal-readout span:nth-child(6) {
  border-top-color: rgba(255, 138, 114, 0.64);
}

.theme-aurora .login-panel {
  border-color: rgba(124, 241, 191, 0.25);
  border-left: 2px solid rgba(255, 138, 114, 0.68);
  border-radius: 7px 2px 7px 2px;
  background: var(--preview-panel);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.055),
    0 30px 80px rgba(0, 0, 0, 0.43),
    0 0 50px rgba(66, 196, 139, 0.07);
}

.theme-aurora .login-panel::before {
  top: 34px;
  left: 0;
  width: 2px;
  height: 88px;
  background: #ff8a72;
  box-shadow: 0 0 16px rgba(255, 138, 114, 0.55);
}

.theme-aurora .login-panel::after {
  border-color: rgba(124, 241, 191, 0.56);
}

.theme-aurora .panel-status {
  border-color: rgba(124, 241, 191, 0.3);
}

.theme-aurora .panel-status::before {
  border-color: rgba(124, 241, 191, 0.45);
}

.theme-aurora .panel-status i {
  background: #7cf1bf;
  box-shadow: 0 0 12px rgba(124, 241, 191, 0.62);
}

.theme-aurora .login-form :deep(.arco-input-wrapper:hover) {
  border-color: rgba(124, 241, 191, 0.5);
}

.theme-aurora .login-form :deep(.arco-input-wrapper.arco-input-focus) {
  border-color: #7cf1bf;
  background: rgba(5, 19, 15, 0.96);
  box-shadow: 0 0 0 3px rgba(94, 219, 164, 0.12), inset 3px 0 0 #7cf1bf;
}

.theme-aurora .reset-button:hover {
  border-color: rgba(124, 241, 191, 0.54) !important;
}

.theme-aurora .login-button {
  border-color: #ff8a72 !important;
  color: #190b08 !important;
  background: #ff8a72 !important;
  box-shadow: 0 10px 28px rgba(255, 138, 114, 0.16);
}

.theme-aurora .login-button:hover {
  border-color: #ffb4a4 !important;
  background: #ffa08c !important;
  box-shadow: 0 12px 32px rgba(255, 138, 114, 0.24);
}

@keyframes signal {
  0%, 100% { opacity: 0.45; transform: scaleY(0.72); }
  50% { opacity: 1; transform: scaleY(1); }
}

@keyframes rotate {
  to { transform: rotate(360deg); }
}

@keyframes ticker-left {
  from { transform: translateX(0); }
  to { transform: translateX(-50%); }
}

@keyframes ticker-right {
  from { transform: translateX(-50%); }
  to { transform: translateX(0); }
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

  .preview-switcher {
    top: 84px;
    right: 18px;
    left: 18px;
    width: auto;
    transform: none;
  }

  .preview-switcher-label {
    display: none;
  }

  .preview-options {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    width: 100%;
  }

  .scene-effect-switcher {
    top: 136px;
    right: 18px;
    left: 18px;
    width: auto;
    transform: none;
  }

  .scene-effect-label {
    display: none;
  }

  .scene-effect-options {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    width: 100%;
  }

  .login-layout {
    display: flex;
    flex-direction: column;
    justify-content: center;
    min-height: 100dvh;
    padding: 74px 18px 54px;
  }

  .preview-active .login-layout {
    padding-top: 196px;
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

  .preview-switcher {
    right: 12px;
    left: 12px;
  }

  .preview-options button {
    padding: 0 5px;
    font-size: 11px;
  }

  .preview-options button span {
    margin-right: 3px;
  }

  .scene-effect-switcher {
    right: 12px;
    left: 12px;
  }

  .scene-effect-options button {
    padding: 0 3px;
    font-size: 10px;
  }

  .scene-effect-options button span {
    margin-right: 2px;
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

  .preview-active .login-layout {
    padding-top: 196px;
  }
}

@media (max-height: 680px) and (min-width: 861px) {
  .login-layout {
    padding-top: 40px;
    padding-bottom: 40px;
  }

  .preview-active .login-layout {
    padding-top: 140px;
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
  .panel-status::before,
  .ticker-row {
    animation: none;
  }

  .cursor-reticle {
    display: none;
  }

  .login-panel {
    transform: none;
    transition: none;
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
