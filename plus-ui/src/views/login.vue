<template>
  <div class="login">
    <!-- 背景层：数据网络动画 -->
    <canvas ref="networkCanvas" class="network-bg"></canvas>
    <!-- CSS 叠加层：透视网格 + 光晕 + 暗角 -->
    <div class="bg-grid" aria-hidden="true"></div>
    <div class="bg-glow glow-blue" aria-hidden="true"></div>
    <div class="bg-glow glow-violet" aria-hidden="true"></div>
    <div class="bg-glow glow-cyan" aria-hidden="true"></div>
    <div class="bg-vignette" aria-hidden="true"></div>

    <!-- 内容层 -->
    <div class="login-content">
      <!-- 品牌头 -->
      <header class="brand">
        <div class="brand-logo">
          <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <linearGradient id="nx-logo-bg" x1="0" y1="0" x2="64" y2="64" gradientUnits="userSpaceOnUse">
                <stop stop-color="#1e2a52" />
                <stop offset="1" stop-color="#0b0f24" />
              </linearGradient>
              <linearGradient id="nx-logo-ring" x1="13.8" y1="11" x2="50.2" y2="53" gradientUnits="userSpaceOnUse">
                <stop stop-color="#409EFF" />
                <stop offset=".35" stop-color="#22d3ee" />
                <stop offset=".7" stop-color="#8b5cf6" />
                <stop offset="1" stop-color="#34d399" />
              </linearGradient>
            </defs>
            <rect width="64" height="64" rx="16" fill="url(#nx-logo-bg)" />
            <path
              d="M32 11L50.2 21.5V42.5L32 53L13.8 42.5V21.5Z"
              fill="rgba(255,255,255,.04)"
              stroke="url(#nx-logo-ring)"
              stroke-width="4.4"
              stroke-linejoin="round"
            />
            <g fill="none" stroke="rgba(255,255,255,.4)" stroke-width="2.2" stroke-linecap="round">
              <path d="M32 32L50.2 21.5M32 32L50.2 42.5M32 32L13.8 42.5M32 32L13.8 21.5" />
            </g>
            <circle cx="13.8" cy="21.5" r="7.2" fill="#409EFF" opacity=".25" />
            <circle cx="50.2" cy="21.5" r="7.2" fill="#22d3ee" opacity=".25" />
            <circle cx="50.2" cy="42.5" r="7.2" fill="#8b5cf6" opacity=".25" />
            <circle cx="13.8" cy="42.5" r="7.2" fill="#34d399" opacity=".25" />
            <circle cx="13.8" cy="21.5" r="4.6" fill="#409EFF" />
            <circle cx="50.2" cy="21.5" r="4.6" fill="#22d3ee" />
            <circle cx="50.2" cy="42.5" r="4.6" fill="#8b5cf6" />
            <circle cx="13.8" cy="42.5" r="4.6" fill="#34d399" />
            <circle cx="32" cy="32" r="9" fill="none" stroke="rgba(255,255,255,.3)" stroke-width="1.4" />
            <circle cx="32" cy="32" r="6" fill="#fff" />
          </svg>
        </div>
        <h1 class="brand-name">{{ logoTitle }}</h1>
        <p class="brand-tagline">ERP · MES · IoT · AI 一体化智能工业平台</p>
      </header>

      <!-- 玻璃登录卡 -->
      <div class="login-card">
        <div class="form-header">
          <h2 class="form-title">欢迎登录</h2>
          <p class="form-subtitle">{{ title }}</p>
        </div>

        <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form" size="large">
          <el-form-item v-if="tenantEnabled" prop="tenantId">
            <el-select
              v-model="loginForm.tenantId"
              filterable
              :placeholder="'请选择/输入公司名称'"
              popper-class="login-select-popper"
              style="width: 100%"
            >
              <el-option v-for="item in tenantList" :key="item.tenantId" :label="item.companyName" :value="item.tenantId" />
              <template #prefix><svg-icon icon-class="company" class="input-icon" /></template>
            </el-select>
          </el-form-item>

          <el-form-item prop="username">
            <el-input v-model="loginForm.username" auto-complete="off" :placeholder="'用户名 / 手机号'">
              <template #prefix><svg-icon icon-class="user" class="input-icon" /></template>
            </el-input>
          </el-form-item>

          <el-form-item prop="password">
            <el-input v-model="loginForm.password" type="password" auto-complete="off" show-password :placeholder="'密码'" @keyup.enter="handleLogin">
              <template #prefix><svg-icon icon-class="password" class="input-icon" /></template>
            </el-input>
          </el-form-item>

          <el-form-item v-if="captchaEnabled" prop="code">
            <el-input v-model="loginForm.code" auto-complete="off" :placeholder="'验证码'" style="flex: 1" @keyup.enter="handleLogin">
              <template #prefix><svg-icon icon-class="validCode" class="input-icon" /></template>
            </el-input>
            <div class="captcha-box" title="点击刷新验证码">
              <img :src="codeUrl" class="captcha-img" @click="getCode" />
            </div>
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="loginForm.rememberMe" class="remember-me">记住我</el-checkbox>
            <router-link v-if="register" class="link-type" :to="'/register'">立即注册</router-link>
          </div>

          <el-form-item class="login-btn-item">
            <el-button :loading="loading" type="primary" class="login-btn" @click.prevent="handleLogin">
              <span v-if="!loading">登 录</span>
              <span v-else>登 录 中...</span>
            </el-button>
          </el-form-item>
        </el-form>

        <div class="social-section">
          <div class="social-divider">
            <span class="social-divider-text">其他登录方式</span>
          </div>
          <div class="social-icons">
            <div class="social-item" :title="'微信登录'" @click="doSocialLogin('wechat_open')">
              <svg-icon icon-class="wechat" />
            </div>
            <div class="social-item" :title="'MaxKey登录'" @click="doSocialLogin('maxkey')">
              <svg-icon icon-class="maxkey" />
            </div>
            <div class="social-item" :title="'TopIam登录'" @click="doSocialLogin('topiam')">
              <svg-icon icon-class="topiam" />
            </div>
            <div class="social-item" :title="'Gitee登录'" @click="doSocialLogin('gitee')">
              <svg-icon icon-class="gitee" />
            </div>
            <div class="social-item" :title="'Github登录'" @click="doSocialLogin('github')">
              <svg-icon icon-class="github" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 页脚 -->
    <footer class="login-footer">Copyright © 2025-2026 WangHuan All Rights Reserved.</footer>
  </div>
</template>

<script setup lang="ts">
import { getCodeImg, getTenantList } from '@/api/login';
import { authRouterUrl } from '@/api/system/social/auth';
import { useUserStore } from '@/store/modules/user';
import { LoginData, TenantVO } from '@/api/types';
import { to } from 'await-to-js';
import { HttpStatus } from '@/enums/RespEnum';

const title = import.meta.env.VITE_APP_TITLE;
const logoTitle = import.meta.env.VITE_APP_LOGO_TITLE || 'NexusERP';
const userStore = useUserStore();
const router = useRouter();

// ==================== 四大模块（融合一体化展示） ====================


// ==================== 数据网络背景 ====================
const networkCanvas = ref<HTMLCanvasElement | null>(null);
const NETWORK_COLORS = ['#409EFF', '#22d3ee', '#8b5cf6', '#34d399'];
let networkAnimId = 0;
let resizeHandler: (() => void) | null = null;
let visibilityHandler: (() => void) | null = null;

const initNetwork = () => {
  const canvas = networkCanvas.value;
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  let width = window.innerWidth;
  let height = window.innerHeight;

  interface NetNode {
    x: number;
    y: number;
    vx: number;
    vy: number;
    r: number;
    color: string;
  }
  interface Pulse {
    a: number;
    b: number;
    t: number;
    speed: number;
    color: string;
  }

  const isMobile = width < 768;
  const NODE_COUNT = isMobile ? 32 : 64;
  const LINK_DIST = isMobile ? 110 : 150;
  const nodes: NetNode[] = [];
  let pulses: Pulse[] = [];
  const rand = (min: number, max: number) => min + Math.random() * (max - min);

  for (let i = 0; i < NODE_COUNT; i++) {
    nodes.push({
      x: Math.random() * width,
      y: Math.random() * height,
      vx: rand(-0.35, 0.35),
      vy: rand(-0.35, 0.35),
      r: rand(1, 2.6),
      color: NETWORK_COLORS[i % NETWORK_COLORS.length]
    });
  }

  const applySize = () => {
    width = window.innerWidth;
    height = window.innerHeight;
    canvas.width = Math.floor(width * dpr);
    canvas.height = Math.floor(height * dpr);
    canvas.style.width = width + 'px';
    canvas.style.height = height + 'px';
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    // 视口变化时将节点钳制回画布内
    nodes.forEach((n) => {
      n.x = Math.min(Math.max(n.x, 0), width);
      n.y = Math.min(Math.max(n.y, 0), height);
    });
  };
  applySize();
  resizeHandler = applySize;
  window.addEventListener('resize', applySize);

  const render = () => {
    ctx.clearRect(0, 0, width, height);

    // 连线
    for (let i = 0; i < nodes.length; i++) {
      const p = nodes[i];
      for (let j = i + 1; j < nodes.length; j++) {
        const q = nodes[j];
        const dx = p.x - q.x;
        const dy = p.y - q.y;
        const dist = Math.hypot(dx, dy);
        if (dist < LINK_DIST) {
          ctx.beginPath();
          ctx.moveTo(p.x, p.y);
          ctx.lineTo(q.x, q.y);
          ctx.strokeStyle = `rgba(100, 160, 255, ${0.12 * (1 - dist / LINK_DIST)})`;
          ctx.lineWidth = 1;
          ctx.stroke();
        }
      }
    }

    // 发光节点
    nodes.forEach((n) => {
      ctx.beginPath();
      ctx.arc(n.x, n.y, n.r, 0, Math.PI * 2);
      ctx.fillStyle = n.color;
      ctx.shadowColor = n.color;
      ctx.shadowBlur = 8;
      ctx.fill();
      ctx.shadowBlur = 0;
    });

    // 数据脉冲（沿活动边流动，位置实时取节点当前坐标）
    pulses.forEach((pl) => {
      const a = nodes[pl.a];
      const b = nodes[pl.b];
      if (!a || !b) return;
      const x = a.x + (b.x - a.x) * pl.t;
      const y = a.y + (b.y - a.y) * pl.t;
      ctx.beginPath();
      ctx.arc(x, y, 1.8, 0, Math.PI * 2);
      ctx.fillStyle = '#ffffff';
      ctx.shadowColor = pl.color;
      ctx.shadowBlur = 12;
      ctx.fill();
      ctx.shadowBlur = 0;
    });
  };

  // 减少动态偏好：只渲染一帧静态网络
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (reduced) {
    render();
    return;
  }

  const spawnPulse = () => {
    if (pulses.length >= 6 || nodes.length < 2) return;
    const a = Math.floor(Math.random() * nodes.length);
    let best = -1;
    let bestDist = LINK_DIST;
    for (let j = 0; j < nodes.length; j++) {
      if (j === a) continue;
      const d = Math.hypot(nodes[a].x - nodes[j].x, nodes[a].y - nodes[j].y);
      if (d < bestDist) {
        bestDist = d;
        best = j;
      }
    }
    if (best === -1) return;
    pulses.push({ a, b: best, t: 0, speed: rand(0.008, 0.02), color: nodes[a].color });
  };

  const update = () => {
    nodes.forEach((n) => {
      n.x += n.vx;
      n.y += n.vy;
      if (n.x <= 0 || n.x >= width) n.vx *= -1;
      if (n.y <= 0 || n.y >= height) n.vy *= -1;
    });
    pulses.forEach((pl) => (pl.t += pl.speed));
    pulses = pulses.filter((pl) => pl.t < 1);
    if (Math.random() < 0.04) spawnPulse();
  };

  let running = true;
  const animate = () => {
    if (!running) return;
    update();
    render();
    networkAnimId = requestAnimationFrame(animate);
  };
  animate();

  // 标签页隐藏时暂停，省电
  visibilityHandler = () => {
    if (document.hidden) {
      running = false;
      cancelAnimationFrame(networkAnimId);
    } else if (!running) {
      running = true;
      animate();
    }
  };
  document.addEventListener('visibilitychange', visibilityHandler);
};

onUnmounted(() => {
  if (networkAnimId) cancelAnimationFrame(networkAnimId);
  if (resizeHandler) window.removeEventListener('resize', resizeHandler);
  if (visibilityHandler) document.removeEventListener('visibilitychange', visibilityHandler);
});

// ==================== 登录表单 ====================
const loginForm = ref<LoginData>({
  tenantId: '000000',
  username: 'admin',
  password: 'admin123',
  rememberMe: false,
  code: '',
  uuid: ''
} as LoginData);

const loginRules: ElFormRules = {
  tenantId: [{ required: true, trigger: 'blur', message: '请输入您的租户编号' }],
  username: [{ required: true, trigger: 'blur', message: '请输入您的账号' }],
  password: [{ required: true, trigger: 'blur', message: '请输入您的密码' }],
  code: [{ required: true, trigger: 'change', message: '请输入验证码' }]
};

const codeUrl = ref('');
const loading = ref(false);
// 验证码开关
const captchaEnabled = ref(true);
// 租户开关
const tenantEnabled = ref(true);
// 注册开关
const register = ref(false);
const redirect = ref('/');
const loginRef = ref<ElFormInstance>();
// 租户列表
const tenantList = ref<TenantVO[]>([]);

watch(
  () => router.currentRoute.value,
  (newRoute: any) => {
    redirect.value = newRoute.query && newRoute.query.redirect && decodeURIComponent(newRoute.query.redirect);
  },
  { immediate: true }
);

const handleLogin = () => {
  loginRef.value?.validate(async (valid: boolean) => {
    if (valid) {
      loading.value = true;
      // 勾选了需要记住密码设置在 localStorage 中设置记住用户名和密码
      if (loginForm.value.rememberMe) {
        localStorage.setItem('tenantId', String(loginForm.value.tenantId));
        localStorage.setItem('username', String(loginForm.value.username));
        localStorage.setItem('password', String(loginForm.value.password));
        localStorage.setItem('rememberMe', String(loginForm.value.rememberMe));
      } else {
        // 否则移除
        localStorage.removeItem('tenantId');
        localStorage.removeItem('username');
        localStorage.removeItem('password');
        localStorage.removeItem('rememberMe');
      }
      // 调用action的登录方法
      const [err] = await to(userStore.login(loginForm.value));
      if (!err) {
        const redirectUrl = redirect.value || '/';
        await router.push(redirectUrl);
        loading.value = false;
      } else {
        loading.value = false;
        // 重新获取验证码
        if (captchaEnabled.value) {
          await getCode();
        }
      }
    }
  });
};

/**
 * 获取验证码
 */
const getCode = async () => {
  const res = await getCodeImg();
  const { data } = res;
  captchaEnabled.value = data.captchaEnabled === undefined ? true : data.captchaEnabled;
  if (captchaEnabled.value) {
    // 刷新验证码时清空输入框
    loginForm.value.code = '';
    codeUrl.value = 'data:image/gif;base64,' + data.img;
    loginForm.value.uuid = data.uuid;
  }
};

const getLoginData = () => {
  const tenantId = localStorage.getItem('tenantId');
  const username = localStorage.getItem('username');
  const password = localStorage.getItem('password');
  const rememberMe = localStorage.getItem('rememberMe');
  loginForm.value = {
    tenantId: tenantId === null ? String(loginForm.value.tenantId) : tenantId,
    username: username === null ? String(loginForm.value.username) : username,
    password: password === null ? String(loginForm.value.password) : String(password),
    rememberMe: rememberMe === null ? false : Boolean(rememberMe)
  } as LoginData;
};

/**
 * 获取租户列表
 */
const initTenantList = async () => {
  const { data } = await getTenantList(false);
  tenantEnabled.value = data.tenantEnabled === undefined ? true : data.tenantEnabled;
  if (tenantEnabled.value) {
    tenantList.value = data.voList;
    if (tenantList.value != null && tenantList.value.length !== 0) {
      loginForm.value.tenantId = tenantList.value[0].tenantId;
    }
  }
};

/**
 * 第三方登录
 * @param type
 */
const doSocialLogin = (type: string) => {
  authRouterUrl(type, loginForm.value.tenantId).then((res: any) => {
    if (res.code === HttpStatus.SUCCESS) {
      // 获取授权地址跳转
      window.location.href = res.data;
    } else {
      ElMessage.error(res.msg);
    }
  });
};

onMounted(() => {
  getCode();
  initTenantList();
  getLoginData();
  initNetwork();
});
</script>

<style lang="scss" scoped>
.login {
  position: relative;
  display: flex;
  min-height: 100vh;
  padding: 56px 24px 88px;
  background: linear-gradient(160deg, #05080f 0%, #0a1120 50%, #05080f 100%);
  overflow-x: hidden;
}

/* ========== 背景层 ========== */
.network-bg {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 0;
  pointer-events: none;
}

.bg-grid {
  position: fixed;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(64, 158, 255, 0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(64, 158, 255, 0.05) 1px, transparent 1px);
  background-size: 44px 44px;
  -webkit-mask-image: radial-gradient(ellipse at center, black 25%, transparent 78%);
  mask-image: radial-gradient(ellipse at center, black 25%, transparent 78%);
  animation: gridDrift 28s linear infinite;
}

.bg-glow {
  position: fixed;
  z-index: 1;
  border-radius: 50%;
  filter: blur(90px);
  pointer-events: none;
}

.glow-blue {
  top: -140px;
  left: -120px;
  width: 520px;
  height: 520px;
  background: radial-gradient(circle, rgba(64, 158, 255, 0.32), transparent 70%);
  animation: breathe 9s ease-in-out infinite;
}

.glow-violet {
  bottom: -160px;
  right: -140px;
  width: 540px;
  height: 540px;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.28), transparent 70%);
  animation: breathe 11s ease-in-out infinite 1.5s;
}

.glow-cyan {
  top: 38%;
  left: 52%;
  width: 420px;
  height: 420px;
  background: radial-gradient(circle, rgba(34, 211, 238, 0.2), transparent 70%);
  animation: breathe 13s ease-in-out infinite 3s;
}

.bg-vignette {
  position: fixed;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background: radial-gradient(ellipse at center, transparent 52%, rgba(3, 6, 12, 0.62) 100%);
}

/* ========== 内容层 ========== */
.login-content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  max-width: 440px;
  margin: auto;
}

/* ========== 品牌头 ========== */
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 28px;
  text-align: center;
  animation: fadeUp 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.brand-logo {
  margin-bottom: 16px;

  svg {
    width: 62px;
    height: 62px;
    filter: drop-shadow(0 8px 24px rgba(64, 158, 255, 0.45));
  }
}

.brand-name {
  margin: 0 0 10px 0;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 2px;
  background: linear-gradient(90deg, #ffffff 0%, #b9d6ff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  color: transparent;
}

.brand-tagline {
  margin: 0;
  font-size: 13px;
  letter-spacing: 3px;
  color: #8b98ad;
}

/* ========== 玻璃登录卡 ========== */
.login-card {
  width: 100%;
  padding: 34px 34px 26px;
  background: rgba(15, 23, 42, 0.55);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--app-radius-lg);
  box-shadow:
    0 8px 40px rgba(0, 0, 0, 0.5),
    0 0 80px rgba(64, 158, 255, 0.08);
  backdrop-filter: blur(20px) saturate(140%);
  -webkit-backdrop-filter: blur(20px) saturate(140%);
  animation: cardEnter 0.7s cubic-bezier(0.22, 1, 0.36, 1) 0.1s both;
}

.form-header {
  margin-bottom: 26px;
  text-align: center;
}

.form-title {
  margin: 0 0 6px 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #e8eef7;
}

.form-subtitle {
  margin: 0;
  font-size: 13px;
  color: #5b6678;
}

/* ========== 表单控件（深色玻璃） ========== */
.login-form {
  .el-form-item {
    margin-bottom: 18px;
  }

  .input-icon {
    width: 16px;
    height: 16px;
    color: #8b98ad;
  }

  :deep(.el-input__wrapper) {
    height: 44px;
    padding: 0 12px;
    background: rgba(255, 255, 255, 0.04);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: var(--app-radius-md);
    box-shadow: none;
    transition:
      border-color 0.2s,
      box-shadow 0.2s,
      background 0.2s;

    &:hover {
      border-color: rgba(255, 255, 255, 0.2);
    }

    &.is-focus {
      background: rgba(255, 255, 255, 0.06);
      border-color: #409eff;
      box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.15);
    }
  }

  :deep(.el-input__inner) {
    line-height: 44px;
    color: #e8eef7;

    &::placeholder {
      color: #5b6678;
    }
  }

  :deep(.el-input__prefix) {
    color: #8b98ad;
  }

  :deep(.el-input__suffix) {
    color: #8b98ad;
  }

  /* 租户下拉（Element Plus 2.5+ 新结构） */
  :deep(.el-select__wrapper) {
    min-height: 44px;
    padding: 0 12px;
    background: rgba(255, 255, 255, 0.04);
    border: 1px solid rgba(255, 255, 255, 0.1);
    border-radius: var(--app-radius-md);
    box-shadow: none;
    transition:
      border-color 0.2s,
      box-shadow 0.2s,
      background 0.2s;

    &:hover {
      border-color: rgba(255, 255, 255, 0.2);
    }

    &.is-focused {
      background: rgba(255, 255, 255, 0.06);
      border-color: #409eff;
      box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.15);
    }
  }

  :deep(.el-select__placeholder),
  :deep(.el-select__selected-item) {
    color: #e8eef7;
  }

  :deep(.el-select__placeholder.is-transparent) {
    color: #5b6678;
  }

  :deep(.el-select__caret),
  :deep(.el-select__icon) {
    color: #8b98ad;
  }
}

.form-options {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;

  .link-type {
    font-size: 13px;
    color: #409eff;
  }
}

.remember-me {
  :deep(.el-checkbox__label) {
    font-size: 13px;
    color: #8b98ad;
  }

  :deep(.el-checkbox__inner) {
    background: rgba(255, 255, 255, 0.04);
    border-color: rgba(255, 255, 255, 0.22);
  }

  :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
    background: #409eff;
    border-color: #409eff;
  }

  :deep(.el-checkbox__input.is-checked + .el-checkbox__label) {
    color: #b9d6ff;
  }
}

.login-btn-item {
  margin-bottom: 0;
}

.login-btn {
  width: 100%;
  height: 46px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 3px;
  color: #fff;
  background: linear-gradient(90deg, #409eff 0%, #2f7df6 100%);
  border: none;
  border-radius: var(--app-radius-md);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.35);
  transition:
    transform 0.2s,
    box-shadow 0.2s,
    background 0.2s;

  &:hover {
    background: linear-gradient(90deg, #54a6ff 0%, #3d86f7 100%);
    box-shadow: 0 8px 26px rgba(64, 158, 255, 0.5);
    transform: translateY(-1px);
  }

  &:active {
    transform: translateY(0);
  }
}

/* ========== 验证码 ========== */
.captcha-box {
  flex-shrink: 0;
  width: 112px;
  height: 44px;
  margin-left: 10px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: var(--app-radius-md);
  cursor: pointer;

  .captcha-img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

/* ========== 社交登录 ========== */
.social-section {
  margin-top: 26px;
}

.social-divider {
  display: flex;
  align-items: center;
  margin-bottom: 16px;

  &::before,
  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: rgba(255, 255, 255, 0.08);
  }
}

.social-divider-text {
  padding: 0 12px;
  font-size: 12.5px;
  color: #5b6678;
}

.social-icons {
  display: flex;
  justify-content: center;
  gap: 14px;
}

.social-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  font-size: 18px;
  color: #8b98ad;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    color: #409eff;
    background: rgba(64, 158, 255, 0.1);
    border-color: rgba(64, 158, 255, 0.5);
    box-shadow: 0 4px 14px rgba(64, 158, 255, 0.25);
    transform: translateY(-2px);
  }
}

/* ========== 页脚 ========== */
.login-footer {
  position: absolute;
  right: 0;
  bottom: 20px;
  left: 0;
  z-index: 2;
  font-size: 12px;
  letter-spacing: 0.5px;
  color: #4b5563;
  text-align: center;
}

/* ========== 动画关键帧 ========== */
@keyframes cardEnter {
  from {
    opacity: 0;
    transform: translateY(30px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes chipIn {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.9);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes chipGlow {
  0%,
  100% {
    box-shadow: 0 0 10px rgba(var(--chip-rgb), 0.1);
  }
  50% {
    box-shadow: 0 0 20px rgba(var(--chip-rgb), 0.3);
  }
}

@keyframes breathe {
  0%,
  100% {
    opacity: 0.45;
    transform: scale(1);
  }
  50% {
    opacity: 0.68;
    transform: scale(1.15);
  }
}

@keyframes gridDrift {
  from {
    background-position:
      0 0,
      0 0;
  }
  to {
    background-position:
      44px 44px,
      44px 44px;
  }
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .login {
    padding: 40px 18px 76px;
  }

  .brand {
    margin-bottom: 22px;
  }

  .brand-logo svg {
    width: 52px;
    height: 52px;
  }

  .brand-name {
    font-size: 26px;
  }

  .brand-tagline {
    font-size: 12px;
    letter-spacing: 2px;
  }

  .login-card {
    padding: 26px 22px 22px;
  }

  .bg-grid {
    display: none;
  }
}

/* ========== 减少动态偏好 ========== */
@media (prefers-reduced-motion: reduce) {
  .bg-grid,
  .bg-glow,
  .brand,
  .login-card,
  .module-chip {
    animation: none !important;
  }
}
</style>

<!-- 租户下拉弹层（teleport 到 body，scoped 样式无法命中，需全局） -->
<style lang="scss">
.login-select-popper.el-popper.is-light {
  background: rgba(13, 20, 34, 0.94);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: var(--app-radius-md);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(12px);

  .el-popper__arrow::before {
    background: rgba(13, 20, 34, 0.94);
    border-color: rgba(255, 255, 255, 0.1);
  }

  .el-select-dropdown__item {
    color: #c4cddc;

    &.is-hovering {
      background: rgba(64, 158, 255, 0.16);
    }

    &.is-selected {
      font-weight: 600;
      color: #409eff;
    }
  }

  .el-select-dropdown__empty,
  .el-select-dropdown__loading {
    color: #5b6678;
  }
}
</style>
