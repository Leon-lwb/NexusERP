<template>
  <div class="login">
    <!-- 动态背景粒子 -->
    <canvas ref="particleCanvas" class="particle-bg"></canvas>

    <div class="login-card">
      <!-- 左侧品牌面板 -->
      <div class="login-brand" :data-module="currentModule">
        <div class="brand-body">
          <div class="brand-logo">
            <svg viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
              <rect width="64" height="64" rx="16" fill="rgba(255,255,255,0.15)"/>
              <path d="M20 22h24v4H20zM20 30h24v4H20zM20 38h16v4H20z" fill="rgba(255,255,255,0.9)"/>
              <circle cx="46" cy="40" r="8" fill="#34d399" stroke="rgba(255,255,255,0.3)" stroke-width="2"/>
              <path d="M44 40l1.5 1.5 3-3" stroke="#fff" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <h1 class="brand-name">{{ title }}</h1>
          <p class="brand-desc">企业资源计划管理系统</p>

          <!-- 模块切换标签 -->
          <div class="module-tabs">
            <div
              v-for="mod in modules"
              :key="mod.key"
              class="module-tab"
              :class="{ active: currentModule === mod.key }"
              @click="switchModule(mod.key)"
            >
              <svg-icon :icon-class="mod.icon" class="module-icon" />
              <span class="module-label">{{ mod.label }}</span>
            </div>
          </div>

          <div class="brand-features">
            <div class="brand-feature">
              <span class="feature-dot"></span>多租户数据隔离
            </div>
            <div class="brand-feature">
              <span class="feature-dot"></span>生产制造全流程
            </div>
            <div class="brand-feature">
              <span class="feature-dot"></span>智能仓储与物流
            </div>
            <div class="brand-feature">
              <span class="feature-dot"></span>销售订单一体化
            </div>
          </div>
        </div>
        <div class="brand-footer">
          Copyright © 2025-2026 WangHuan All Rights Reserved.
        </div>
      </div>

      <!-- 右侧表单面板 -->
      <div class="login-form-panel">
        <div class="form-wrapper">
          <div class="form-header">
            <h2 class="form-title">欢迎登录</h2>
            <p class="form-subtitle">{{ moduleSubtitle }}</p>
            <lang-select class="lang-position" />
          </div>

          <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form" size="large">
            <el-form-item v-if="tenantEnabled" prop="tenantId">
              <el-select v-model="loginForm.tenantId" filterable :placeholder="proxy.$t('login.selectPlaceholder')" style="width: 100%">
                <el-option v-for="item in tenantList" :key="item.tenantId" :label="item.companyName" :value="item.tenantId" />
                <template #prefix><svg-icon icon-class="company" class="el-input__icon input-icon" /></template>
              </el-select>
            </el-form-item>

            <el-form-item prop="username">
              <el-input v-model="loginForm.username" auto-complete="off" :placeholder="proxy.$t('login.username')">
                <template #prefix><svg-icon icon-class="user" class="el-input__icon input-icon" /></template>
              </el-input>
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                auto-complete="off"
                :placeholder="proxy.$t('login.password')"
                @keyup.enter="handleLogin"
              >
                <template #prefix><svg-icon icon-class="password" class="el-input__icon input-icon" /></template>
              </el-input>
            </el-form-item>

            <el-form-item v-if="captchaEnabled" prop="code">
              <el-input
                v-model="loginForm.code"
                auto-complete="off"
                :placeholder="proxy.$t('login.code')"
                style="flex: 1"
                @keyup.enter="handleLogin"
              >
                <template #prefix><svg-icon icon-class="validCode" class="el-input__icon input-icon" /></template>
              </el-input>
              <div class="captcha-box">
                <img :src="codeUrl" class="captcha-img" @click="getCode" />
              </div>
            </el-form-item>

            <el-checkbox v-model="loginForm.rememberMe" class="remember-me">{{ proxy.$t('login.rememberPassword') }}</el-checkbox>

            <el-form-item>
              <el-button :loading="loading" type="primary" style="width: 100%" @click.prevent="handleLogin">
                <span v-if="!loading">{{ proxy.$t('login.login') }}</span>
                <span v-else>{{ proxy.$t('login.logging') }}</span>
              </el-button>
            </el-form-item>

            <div v-if="register" class="register-link">
              <router-link class="link-type" :to="'/register'">{{ proxy.$t('login.switchRegisterPage') }}</router-link>
            </div>
          </el-form>

          <div class="social-section">
            <div class="social-divider">
              <span class="social-divider-text">其他登录方式</span>
            </div>
            <div class="social-icons">
              <div class="social-item" :title="proxy.$t('login.social.wechat')" @click="doSocialLogin('wechat_open')">
                <svg-icon icon-class="wechat" />
              </div>
              <div class="social-item" :title="proxy.$t('login.social.maxkey')" @click="doSocialLogin('maxkey')">
                <svg-icon icon-class="maxkey" />
              </div>
              <div class="social-item" :title="proxy.$t('login.social.topiam')" @click="doSocialLogin('topiam')">
                <svg-icon icon-class="topiam" />
              </div>
              <div class="social-item" :title="proxy.$t('login.social.gitee')" @click="doSocialLogin('gitee')">
                <svg-icon icon-class="gitee" />
              </div>
              <div class="social-item" :title="proxy.$t('login.social.github')" @click="doSocialLogin('github')">
                <svg-icon icon-class="github" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { getCodeImg, getTenantList } from '@/api/login';
import { authRouterUrl } from '@/api/system/social/auth';
import { useUserStore } from '@/store/modules/user';
import { LoginData, TenantVO } from '@/api/types';
import { to } from 'await-to-js';
import { HttpStatus } from '@/enums/RespEnum';
import { useI18n } from 'vue-i18n';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const title = import.meta.env.VITE_APP_TITLE;
const userStore = useUserStore();
const router = useRouter();
const { t } = useI18n();

// ==================== 模块切换 ====================
interface ModuleItem {
  key: string;
  label: string;
  icon: string;
  subtitle: string;
  theme: string;
}

const modules = ref<ModuleItem[]>([
  { key: 'erp', label: 'ERP', icon: 'dashboard', subtitle: '企业资源计划 · 登录', theme: '#2563eb' },
  { key: 'mes', label: 'MES', icon: 'monitor', subtitle: '制造执行系统 · 登录', theme: '#0891b2' },
  { key: 'iot', label: 'IOT', icon: 'international', subtitle: '物联网平台 · 登录', theme: '#7c3aed' }
]);

const currentModule = ref('erp');
const moduleSubtitle = computed(() => modules.value.find(m => m.key === currentModule.value)?.subtitle || '请输入账号信息以继续');
const currentTheme = computed(() => modules.value.find(m => m.key === currentModule.value)?.theme || '#2563eb');

const switchModule = (key: string) => {
  currentModule.value = key;
  // 可扩展：根据模块切换不同的登录接口或跳转路径
};

// ==================== 粒子背景 ====================
const particleCanvas = ref<HTMLCanvasElement | null>(null);
let particleAnimId = 0;

const initParticles = () => {
  const canvas = particleCanvas.value;
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  const resize = () => {
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;
  };
  resize();
  window.addEventListener('resize', resize);

  const particles: { x: number; y: number; vx: number; vy: number; radius: number; alpha: number }[] = [];
  const count = 45;
  for (let i = 0; i < count; i++) {
    particles.push({
      x: Math.random() * canvas.width,
      y: Math.random() * canvas.height,
      vx: (Math.random() - 0.5) * 0.4,
      vy: (Math.random() - 0.5) * 0.4,
      radius: Math.random() * 2 + 1,
      alpha: Math.random() * 0.5 + 0.2
    });
  }

  const animate = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    particles.forEach((p, i) => {
      p.x += p.vx;
      p.y += p.vy;
      if (p.x < 0 || p.x > canvas.width) p.vx *= -1;
      if (p.y < 0 || p.y > canvas.height) p.vy *= -1;

      ctx.beginPath();
      ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(100, 116, 139, ${p.alpha})`;
      ctx.fill();

      // 连线
      for (let j = i + 1; j < particles.length; j++) {
        const q = particles[j];
        const dx = p.x - q.x;
        const dy = p.y - q.y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 150) {
          ctx.beginPath();
          ctx.moveTo(p.x, p.y);
          ctx.lineTo(q.x, q.y);
          ctx.strokeStyle = `rgba(148, 163, 184, ${0.12 * (1 - dist / 150)})`;
          ctx.lineWidth = 1;
          ctx.stroke();
        }
      }
    });
    particleAnimId = requestAnimationFrame(animate);
  };
  animate();
};

onUnmounted(() => {
  if (particleAnimId) cancelAnimationFrame(particleAnimId);
});

const loginForm = ref<LoginData>({
  tenantId: '000000',
  username: 'admin',
  password: 'admin123',
  rememberMe: false,
  code: '',
  uuid: ''
} as LoginData);

const loginRules: ElFormRules = {
  tenantId: [{ required: true, trigger: 'blur', message: t('login.rule.tenantId.required') }],
  username: [{ required: true, trigger: 'blur', message: t('login.rule.username.required') }],
  password: [{ required: true, trigger: 'blur', message: t('login.rule.password.required') }],
  code: [{ required: true, trigger: 'change', message: t('login.rule.code.required') }]
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
  loginRef.value?.validate(async (valid: boolean, fields: any) => {
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
    } else {
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
  initParticles();
});
</script>

<style lang="scss" scoped>
.login {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: 24px;
  background: linear-gradient(135deg, #f0f4fc 0%, #e2e8f0 50%, #f0f4fc 100%);
  position: relative;
  overflow: hidden;
}

.particle-bg {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  z-index: 0;
}

.login-card {
  display: flex;
  width: min(960px, 100%);
  min-height: 560px;
  border-radius: var(--app-radius-lg);
  box-shadow: var(--app-shadow-lg);
  overflow: hidden;
  background: #fff;
  position: relative;
  z-index: 1;
  animation: cardEnter 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
}

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

/* ========== 左侧品牌面板 ========== */
.login-brand {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  flex: 0 0 44%;
  padding: 48px 40px;
  background: linear-gradient(160deg, #1e3a8a 0%, #2563eb 40%, #3b82f6 100%);
  color: #fff;
  position: relative;
  overflow: hidden;
  transition: background 0.6s ease;

  &::before {
    content: '';
    position: absolute;
    top: -60px;
    right: -60px;
    width: 200px;
    height: 200px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.04);
  }

  &::after {
    content: '';
    position: absolute;
    bottom: -40px;
    left: -40px;
    width: 160px;
    height: 160px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.04);
  }
}

/* 模块主题色 */
.login-brand[data-module='mes'] {
  background: linear-gradient(160deg, #164e63 0%, #0891b2 40%, #06b6d4 100%);
}
.login-brand[data-module='iot'] {
  background: linear-gradient(160deg, #4c1d95 0%, #7c3aed 40%, #8b5cf6 100%);
}

.brand-body {
  position: relative;
  z-index: 1;
}

.brand-logo {
  margin-bottom: 24px;

  svg {
    width: 56px;
    height: 56px;
  }
}

.brand-name {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px 0;
  letter-spacing: 1px;
  color: #fff;
}

.brand-desc {
  font-size: 14px;
  opacity: 0.8;
  margin: 0 0 24px 0;
  letter-spacing: 2px;
}

/* ========== 模块切换标签 ========== */
.module-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 28px;
}

.module-tab {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: var(--app-radius-md);
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.15);
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 13px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(4px);

  .module-icon {
    width: 16px;
    height: 16px;
  }

  &:hover {
    background: rgba(255, 255, 255, 0.18);
    color: #fff;
    transform: translateY(-1px);
  }

  &.active {
    background: rgba(255, 255, 255, 0.95);
    color: #1e293b;
    box-shadow: 0 4px 14px rgba(0, 0, 0, 0.15);
    transform: translateY(-1px);
  }
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.brand-feature {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13.5px;
  opacity: 0.88;
}

.feature-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34d399;
  flex-shrink: 0;
}

.brand-footer {
  position: relative;
  z-index: 1;
  font-size: 12px;
  opacity: 0.55;
  letter-spacing: 0.5px;
}

/* ========== 右侧表单面板 ========== */
.login-form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 40px;
  background: #fff;
}

.form-wrapper {
  width: 100%;
  max-width: 340px;
}

.form-header {
  margin-bottom: 32px;
  position: relative;
}

.lang-position {
  position: absolute;
  top: -4px;
  right: 0;
}

.form-title {
  font-size: 22px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 6px 0;
  letter-spacing: 0.5px;
}

.form-subtitle {
  font-size: 13.5px;
  color: #94a3b8;
  margin: 0;
}

.login-form {
  .el-form-item {
    margin-bottom: 20px;
  }

  :deep(.el-input__wrapper) {
    box-shadow: none;
    background: #f8fafc;
    border: 1px solid #e2e8f0;
    border-radius: var(--app-radius-md);
    padding: 0 12px;
    transition: border-color 0.2s, box-shadow 0.2s;
    height: 42px;

    &:hover {
      border-color: #cbd5e1;
    }

    &.is-focus {
      border-color: #3b82f6;
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
      background: #fff;
    }
  }

  :deep(.el-input__inner) {
    line-height: 42px;
  }

  .input-icon {
    width: 16px;
    height: 16px;
  }

  :deep(.el-button--primary) {
    height: 44px;
    border-radius: var(--app-radius-md);
    font-size: 15px;
    font-weight: 600;
    letter-spacing: 2px;
    box-shadow: 0 4px 14px rgba(59, 130, 246, 0.35);
  }
}

.remember-me {
  margin-bottom: 24px;
  :deep(.el-checkbox__label) {
    font-size: 13px;
    color: #64748b;
  }
}

.register-link {
  text-align: center;
  margin-top: -8px;
}

/* ========== 验证码 ========== */
.captcha-box {
  width: 110px;
  height: 42px;
  margin-left: 10px;
  flex-shrink: 0;
  border-radius: var(--app-radius-sm);
  overflow: hidden;
  border: 1px solid #e2e8f0;
  cursor: pointer;

  .captcha-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

/* ========== 社交登录 ========== */
.social-section {
  margin-top: 28px;
}

.social-divider {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
}

.social-divider-text {
  font-size: 12.5px;
  color: #94a3b8;
  padding-right: 12px;
}

.social-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #e2e8f0;
}

.social-icons {
  display: flex;
  justify-content: center;
  gap: 16px;
}

.social-item {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--app-radius-sm);
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 18px;
  color: #64748b;

  &:hover {
    border-color: #3b82f6;
    background: #eff6ff;
    color: #3b82f6;
    transform: translateY(-1px);
    box-shadow: 0 2px 8px rgba(59, 130, 246, 0.12);
  }
}

/* ========== 深色模式 ========== */
:global(html.dark) {
  .login {
    background: linear-gradient(135deg, #0f1115 0%, #151922 50%, #0f1115 100%);
  }

  .login-card {
    background: #1a1d24;
  }

  .login-brand {
    background: linear-gradient(160deg, #0c1d4a 0%, #153575 40%, #1d4fa8 100%);
  }
  .login-brand[data-module='mes'] {
    background: linear-gradient(160deg, #083344 0%, #0e5a6e 40%, #115e75 100%);
  }
  .login-brand[data-module='iot'] {
    background: linear-gradient(160deg, #2e1065 0%, #4c1d95 40%, #5b21b6 100%);
  }

  .login-form-panel {
    background: #1a1d24;
  }

  .form-title {
    color: #e5e7eb;
  }

  .form-subtitle {
    color: #6b7280;
  }

  .login-form :deep(.el-input__wrapper) {
    background: #1f2229;
    border-color: #2d3139;

    &:hover {
      border-color: #3d424d;
    }

    &.is-focus {
      border-color: #3b82f6;
      background: #1a1d24;
    }
  }

  .login-form :deep(.el-button--primary) {
    box-shadow: 0 4px 14px rgba(59, 130, 246, 0.25);
  }

  .remember-me :deep(.el-checkbox__label) {
    color: #9ca3af;
  }

  .captcha-box {
    border-color: #2d3139;
  }

  .social-divider::after {
    background: #2d3139;
  }

  .social-item {
    background: #1f2229;
    border-color: #2d3139;
    color: #9ca3af;

    &:hover {
      border-color: #3b82f6;
      background: rgba(59, 130, 246, 0.08);
      color: #60a5fa;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
    }
  }
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .login {
    padding: 16px;
    align-items: flex-start;
    padding-top: 40px;
  }

  .login-card {
    flex-direction: column;
  }

  .login-brand {
    flex: none;
    padding: 32px 28px;

    .brand-footer {
      display: none;
    }
  }

  .brand-features {
    display: none;
  }

  .brand-desc {
    margin-bottom: 0;
  }

  .login-form-panel {
    padding: 28px 24px 36px;
  }

  .form-header {
    margin-bottom: 24px;
  }
}
</style>
