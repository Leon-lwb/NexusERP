<template>
  <header class="navbar" :class="['nav' + navType, { 'is-mobile': isMobile }]">
    <button
      v-if="navType !== NavTypeEnum.TOP || isMobile"
      id="hamburger-container"
      type="button"
      class="navbar-action sidebar-toggle"
      :aria-label="appStore.sidebar.opened ? '收起侧边导航' : '展开侧边导航'"
      :aria-expanded="appStore.sidebar.opened"
      @click="toggleSideBar"
    >
      <svg-icon :icon-class="appStore.sidebar.opened ? 'nav-panel-close' : 'nav-panel-open'" />
    </button>

    <logo v-if="navType === NavTypeEnum.TOP && !isMobile" v-show="showLogo" :collapse="false" class="navbar-logo" />
    <div class="navbar-context">
      <breadcrumb v-if="navType === NavTypeEnum.LEFT || isMobile" id="breadcrumb-container" class="breadcrumb-container" />
      <top-nav v-else-if="navType === NavTypeEnum.MIX" id="topmenu-container" class="topmenu-container" />
      <top-bar v-else id="topbar-container" class="topbar-container" />
    </div>

    <div class="navbar-tools" role="group" aria-label="工作区工具">
      <el-select
        v-if="canSwitchTenant && !isMobile"
        v-model="selectedTenantId"
        class="tenant-switcher"
        clearable
        filterable
        aria-label="切换租户"
        placeholder="选择租户"
        :loading="tenantLoading"
        :disabled="tenantSwitching"
        @change="dynamicTenantEvent"
        @clear="dynamicClearEvent"
      >
        <el-option v-for="item in tenantList" :key="item.tenantId" :label="item.companyName" :value="item.tenantId" />
        <template #prefix><svg-icon icon-class="company" /></template>
      </el-select>

      <el-tooltip content="搜索菜单" placement="bottom" :show-after="350">
        <button type="button" class="navbar-action" aria-label="搜索菜单" @click="openSearchMenu">
          <svg-icon icon-class="nav-search" />
        </button>
      </el-tooltip>
      <el-tooltip v-if="!isMobile" content="AI 智能助手" placement="bottom" :show-after="350">
        <button type="button" class="navbar-action assistant-action" aria-label="AI 智能助手" @click="openAiChat">
          <svg-icon icon-class="nav-assistant" />
        </button>
      </el-tooltip>
      <el-popover v-model:visible="noticeVisible" placement="bottom-end" trigger="click" :width="320" :persistent="false">
        <template #reference>
          <button
            type="button"
            class="navbar-action"
            :aria-label="newNotice > 0 ? `查看通知，${newNotice} 条未读` : '查看通知'"
            :aria-expanded="noticeVisible"
          >
            <el-badge :value="newNotice" :max="99" :hidden="newNotice <= 0" class="notice-badge" aria-hidden="true">
              <svg-icon icon-class="nav-notification" />
            </el-badge>
          </button>
        </template>
        <notice @navigate="noticeVisible = false" />
      </el-popover>
      <span class="toolbar-divider" aria-hidden="true"></span>
      <el-tooltip v-if="settingsStore.showSettings" content="主题设置" placement="bottom" :show-after="350">
        <button type="button" class="navbar-action" aria-label="主题设置" @click="setTheme">
          <svg-icon icon-class="nav-appearance" />
        </button>
      </el-tooltip>
      <el-dropdown trigger="click" placement="bottom-end" @command="handleCommand" @visible-change="moreVisible = $event">
        <button type="button" class="navbar-action" aria-label="更多工具" aria-haspopup="menu" :aria-expanded="moreVisible">
          <svg-icon icon-class="nav-more" />
        </button>
        <template #dropdown>
          <el-dropdown-menu class="navbar-dropdown">
            <el-dropdown-item v-if="isMobile" command="ai"><svg-icon icon-class="nav-assistant" />AI 智能助手</el-dropdown-item>
            <el-dropdown-item v-if="isMobile && canSwitchTenant" command="tenant"><svg-icon icon-class="company" />切换租户</el-dropdown-item>
            <el-dropdown-item v-if="settingsStore.showSettings" command="setLayout"><svg-icon icon-class="nav-layout" />布局与密度</el-dropdown-item>
            <el-dropdown-item command="fullscreen" :disabled="!fullscreenSupported">
              <svg-icon :icon-class="isFullscreen ? 'nav-fullscreen-exit' : 'nav-fullscreen'" />{{ isFullscreen ? '退出全屏' : '进入全屏' }}
            </el-dropdown-item>
            <el-dropdown-item command="manual" divided><svg-icon icon-class="nav-guide" />项目文档</el-dropdown-item>
            <el-dropdown-item command="source"><svg-icon icon-class="nav-code" />Gitee 仓库</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-dropdown trigger="click" placement="bottom-end" @command="handleCommand">
        <button type="button" class="avatar-button" :aria-label="`${displayName}，打开用户菜单`" aria-haspopup="menu">
          <img :src="avatarSrc" class="user-avatar" alt="用户头像" @error="handleAvatarError" />
          <span class="user-name">{{ displayName }}</span>
          <svg-icon icon-class="nav-chevron" class="avatar-chevron" />
        </button>
        <template #dropdown>
          <el-dropdown-menu class="navbar-dropdown">
            <el-dropdown-item disabled class="account-label">{{ displayName }}</el-dropdown-item>
            <el-dropdown-item v-if="!dynamic" command="profile"><svg-icon icon-class="nav-user" />个人中心</el-dropdown-item>
            <el-dropdown-item v-if="settingsStore.showSettings" command="setLayout"><svg-icon icon-class="nav-layout" />界面设置</el-dropdown-item>
            <el-dropdown-item divided command="logout"><svg-icon icon-class="nav-logout" />退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
  <search-menu ref="searchMenuRef" />
  <AiChatDrawer ref="aiChatDrawerRef" />
  <el-dialog v-if="canSwitchTenant" v-model="tenantDialogVisible" title="切换租户" width="min(420px, calc(100vw - 32px))" append-to-body>
    <p class="tenant-hint">切换租户后，将刷新当前工作区。</p>
    <el-select
      v-model="selectedTenantId"
      class="tenant-dialog-select"
      clearable
      filterable
      aria-label="切换租户"
      placeholder="选择租户"
      :loading="tenantLoading"
      :disabled="tenantSwitching"
      @change="dynamicTenantEvent"
      @clear="dynamicClearEvent"
    >
      <el-option v-for="item in tenantList" :key="item.tenantId" :label="item.companyName" :value="item.tenantId" />
    </el-select>
  </el-dialog>
</template>

<script setup lang="ts">
import SearchMenu from './TopBar/search.vue';
import { useFullscreen } from '@vueuse/core';
import { useAppStore } from '@/store/modules/app';
import { useUserStore } from '@/store/modules/user';
import defaultAvatar from '@/assets/images/profile.jpg';
import { useSettingsStore } from '@/store/modules/settings';
import { useThemeStore } from '@/store/modules/theme';
import { useNoticeStore } from '@/store/modules/notice';
import { getTenantList } from '@/api/login';
import { dynamicClear, dynamicTenant } from '@/api/system/tenant';
import { unreadNotificationCount } from '@/api/im/notification';
import { TenantVO } from '@/api/types';
import notice from './notice/index.vue';
import { NavTypeEnum } from '@/enums/NavTypeEnum';
import Logo from '@/layout/components/Sidebar/Logo.vue';
import TopBar from './TopBar/index.vue';
import AiChatDrawer from '@/components/ai/AiChatDrawer.vue';

const appStore = useAppStore();
const userStore = useUserStore();
// 头像可来自独立部署的对象存储；仅允许 HTTP(S) 且不含 URL 用户名或密码。
const avatarLoadFailed = ref(false);
const avatarSrc = computed(() => {
  const candidate = userStore.avatar;
  if (avatarLoadFailed.value || typeof candidate !== 'string' || !candidate.trim()) return defaultAvatar;
  try {
    const url = new URL(candidate, window.location.origin);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return defaultAvatar;
    return url.href;
  } catch {
    return defaultAvatar;
  }
});
// 失败仅改变展示状态，不写回用户头像；默认资源重复失败时不循环替换src。
const handleAvatarError = () => {
  avatarLoadFailed.value = true;
};
watch(
  () => userStore.avatar,
  () => {
    avatarLoadFailed.value = false;
  }
);
const settingsStore = useSettingsStore();
const themeStore = useThemeStore();
const noticeStore = useNoticeStore();
// 未读角标直读 store（实时 +1 / 已读 -1 由 store 维护），无需本地镜像
const newNotice = computed(() => noticeStore.state.unreadCount);

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const router = useRouter();
const isMobile = computed(() => appStore.device === 'mobile');
const canSwitchTenant = computed(() => String(userStore.userId) === '1' && tenantEnabled.value);
const displayName = computed(() => userStore.nickname || '用户');
const { isFullscreen, isSupported: fullscreenSupported, toggle: toggleFullscreen } = useFullscreen();
const moreVisible = ref(false);
const tenantDialogVisible = ref(false);
const tenantLoading = ref(false);
const tenantSwitching = ref(false);
const committedTenantId = ref<string>();
const navType = computed(() => themeStore.navType);
const showLogo = computed(() => themeStore.sidebarLogo);

const selectedTenantId = ref<string>();
const tenantList = ref<TenantVO[]>([]);
// 是否切换了租户
const dynamic = ref(false);
// 租户开关
const tenantEnabled = ref(true);
// 搜索菜单
const searchMenuRef = ref<InstanceType<typeof SearchMenu>>();
const aiChatDrawerRef = ref<InstanceType<typeof AiChatDrawer>>();
// 消息盒子（el-popover）显隐：通知跳转业务详情页时由子组件事件收起
const noticeVisible = ref(false);

const openSearchMenu = () => {
  searchMenuRef.value?.openSearch();
};

const openAiChat = () => {
  aiChatDrawerRef.value?.open();
};

/** 打开 Gitee 仓库 */
const openGitee = () => {
  window.open('https://gitee.com/NexusERP/nexus-erp', '_blank', 'noopener,noreferrer');
};

/** 打开项目文档（public/manual 随前端站点部署，地址需拼接部署上下文路径） */
const openManual = () => {
  window.open(`${import.meta.env.VITE_APP_CONTEXT_PATH || '/'}manual/index.html`, '_blank', 'noopener,noreferrer');
};

/** 切换当前会话租户；请求失败时恢复已确认的选择，避免界面与会话不一致。 */
const switchTenant = async (tenantId?: string) => {
  if (tenantSwitching.value) return;
  tenantSwitching.value = true;
  try {
    if (tenantId) await dynamicTenant(tenantId);
    else await dynamicClear();
    committedTenantId.value = tenantId;
    dynamic.value = Boolean(tenantId);
    tenantDialogVisible.value = false;
    await router.push('/');
    await proxy?.$tab.closeAllPage();
    await proxy?.$tab.refreshPage();
  } catch {
    // 接口拦截器展示失败原因；选择器回退到服务端已确认的租户。
    selectedTenantId.value = committedTenantId.value;
  } finally {
    tenantSwitching.value = false;
  }
};

/** 清空时由 clear 事件处理，避免 change 与 clear 发出两次请求。 */
const dynamicTenantEvent = (tenantId: string) => {
  if (tenantId) return switchTenant(tenantId);
};
const dynamicClearEvent = () => switchTenant();

/** 仅超级管理员加载租户列表，普通用户不发出管理接口请求。 */
const initTenantList = async () => {
  if (String(userStore.userId) !== '1') return;
  tenantLoading.value = true;
  try {
    const { data } = await getTenantList(true);
    tenantEnabled.value = data.tenantEnabled ?? true;
    tenantList.value = data.voList ?? [];
  } catch {
    tenantEnabled.value = false;
  } finally {
    tenantLoading.value = false;
  }
};

defineExpose({
  initTenantList
});

const toggleSideBar = () => {
  appStore.toggleSideBar(false);
};

const logout = async () => {
  try {
    await ElMessageBox.confirm('确定注销并退出系统吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    });
  } catch {
    return; // 用户取消
  }
  await userStore.logout();
  // 清空通知内存态：同一标签页换账号登录时不残留上一用户的通知与未读数
  noticeStore.clearNotice();
  noticeStore.setUnreadCount(0);
  await router.replace({
    path: '/login',
    query: {
      redirect: encodeURIComponent(router.currentRoute.value.fullPath || '/')
    }
  });
  await proxy?.$tab.closeAllPage();
};

const emits = defineEmits(['setLayout', 'setTheme']);
const setLayout = () => {
  emits('setLayout');
};
const setTheme = () => {
  emits('setTheme');
};
/** 顶部菜单统一分发；返回异步结果交由 Vue 事件机制处理。 */
const commandMap: Record<string, () => void | Promise<unknown>> = {
  setLayout,
  setTheme,
  ai: openAiChat,
  tenant: () => {
    tenantDialogVisible.value = true;
  },
  fullscreen: () => toggleFullscreen(),
  manual: openManual,
  source: openGitee,
  profile: () => router.push('/user/profile'),
  logout
};
const handleCommand = (command: string) => commandMap[command]?.();
// 登录/刷新后初始化未读计数（持久化基数）；实时 +1 / 已读 -1 由 notice store 维护
onMounted(() => {
  unreadNotificationCount()
    .then((res: any) => {
      noticeStore.setUnreadCount(Number(res.data ?? 0));
    })
    .catch(() => {
      /* 接口失败不阻塞 */
    });
});
</script>

<style lang="scss" scoped>
.navbar {
  height: 50px;
  padding: 0 12px 0 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  box-sizing: border-box;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-regular);
}
.navbar-context {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  display: flex;
  align-items: center;
}
.breadcrumb-container,
.topmenu-container,
.topbar-container {
  width: 100%;
  min-width: 0;
}
.breadcrumb-container {
  overflow: hidden;
  white-space: nowrap;
  :deep(.el-breadcrumb__item) {
    float: none;
    display: inline;
  }
  :deep(.el-breadcrumb__inner) {
    color: var(--el-text-color-regular);
  }
  :deep(.no-redirect) {
    color: var(--el-text-color-secondary);
  }
}
.navbar-logo {
  flex-shrink: 0;
}
.navbar-tools {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}
.navbar-action,
.avatar-button {
  appearance: none;
  border: 0;
  background: transparent;
  color: var(--el-text-color-regular);
  font: inherit;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--app-radius-sm);
  transition:
    background-color 0.16s,
    color 0.16s;
  &:hover {
    color: var(--el-color-primary);
    background: var(--el-fill-color-light);
  }
  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: -2px;
  }
  &:active {
    background: var(--el-fill-color);
  }
}
.navbar-action {
  width: 36px;
  height: 36px;
  padding: 8px;
  font-size: 20px;
  flex-shrink: 0;
}
.assistant-action {
  color: var(--el-color-primary);
}
.sidebar-toggle {
  margin-right: 0;
}
.toolbar-divider {
  height: 20px;
  width: 1px;
  background: var(--el-border-color-lighter);
  margin: 0 4px;
}
.tenant-switcher {
  width: clamp(140px, 14vw, 210px);
  margin-right: 8px;
}
.notice-badge {
  display: inline-flex;
  align-items: center;
}
.notice-badge :deep(.el-badge__content) {
  height: 16px;
  line-height: 14px;
  min-width: 16px;
  padding: 0 4px;
  font-size: 10px;
  top: 0;
}
.avatar-button {
  height: 40px;
  padding: 4px 6px;
  gap: 8px;
  margin-left: 4px;
}
.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--el-fill-color-light);
}
.user-name {
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}
.avatar-chevron {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.tenant-hint {
  color: var(--el-text-color-secondary);
  margin-top: 0;
}
.tenant-dialog-select {
  width: 100%;
}
@media (max-width: 1199px) {
  .user-name {
    display: none;
  }
}
@media (max-width: 600px) {
  .navbar {
    padding: 0 4px;
    gap: 0;
  }
  .navbar-tools {
    gap: 0;
  }
  .navbar-action {
    width: 40px;
    height: 44px;
    padding: 10px;
  }
  .avatar-button {
    min-width: 44px;
    height: 44px;
    margin-left: 0;
    padding: 6px;
  }
  .avatar-chevron,
  .toolbar-divider {
    display: none;
  }
  .breadcrumb-container {
    font-size: 12px;
    text-overflow: ellipsis;
  }
}
@media (prefers-reduced-motion: reduce) {
  .navbar-action,
  .avatar-button {
    transition: none;
  }
}
</style>

<style lang="scss">
.navbar-dropdown .el-dropdown-menu__item {
  gap: 10px;
  min-height: 36px;
}
.navbar-dropdown .account-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
