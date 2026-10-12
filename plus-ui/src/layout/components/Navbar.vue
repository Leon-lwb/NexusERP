<template>
  <div class="navbar" :class="'nav' + navType">
    <hamburger id="hamburger-container" :is-active="appStore.sidebar.opened" class="hamburger-container" @toggle-click="toggleSideBar" />

    <breadcrumb v-if="navType == NavTypeEnum.LEFT" id="breadcrumb-container" class="breadcrumb-container" />
    <top-nav v-if="navType == NavTypeEnum.MIX" id="topmenu-container" class="topmenu-container" />

    <template v-if="navType == NavTypeEnum.TOP">
      <logo v-show="showLogo" :collapse="false"></logo>
      <top-bar id="topbar-container" class="topbar-container" />
    </template>
    <div class="right-menu flex align-center">
      <template v-if="appStore.device !== 'mobile'">
        <el-select
          v-if="userId === 1 && tenantEnabled"
          v-model="companyName"
          class="min-w-244px"
          clearable
          filterable
          reserve-keyword
          placeholder="选择租户"
          @change="dynamicTenantEvent"
          @clear="dynamicClearEvent"
        >
          <el-option v-for="item in tenantList" :key="item.tenantId" :label="item.companyName" :value="item.tenantId"> </el-option>
          <template #prefix><svg-icon icon-class="company" class="el-input__icon input-icon" /></template>
        </el-select>

        <search-menu ref="searchMenuRef" />
        <el-tooltip content="搜索" effect="dark" placement="bottom">
          <div class="right-menu-item hover-effect" @click="openSearchMenu">
            <svg-icon class-name="search-icon" icon-class="search" />
          </div>
        </el-tooltip>
        <!-- AI 智能助手 -->
        <el-tooltip content="AI 智能助手" effect="dark" placement="bottom">
          <div class="right-menu-item hover-effect" @click="openAiChat">
            <el-icon :size="18"><ChatDotRound /></el-icon>
          </div>
        </el-tooltip>

        <!-- 消息 -->
        <el-tooltip content="消息" effect="dark" placement="bottom">
          <div>
            <el-popover
              v-model:visible="noticeVisible"
              placement="bottom"
              trigger="click"
              transition="el-zoom-in-top"
              :width="300"
              :persistent="false"
            >
              <template #reference>
                <el-badge :value="newNotice" :max="99" :hidden="newNotice <= 0">
                  <div class="right-menu-item hover-effect" style="display: block"><svg-icon icon-class="message" /></div>
                </el-badge>
              </template>
              <template #default>
                <!-- 通知带跳转目标时（流程待办/结束通知）点击会直接跳转，需收起消息盒子避免遮挡目标页面 -->
                <notice @navigate="noticeVisible = false"></notice>
              </template>
            </el-popover>
          </div>
        </el-tooltip>
        <el-tooltip content="Gitee 仓库" effect="dark" placement="bottom">
          <div class="right-menu-item hover-effect" @click="openGitee">
            <svg-icon icon-class="gitee" />
          </div>
        </el-tooltip>

        <el-tooltip content="项目文档" effect="dark" placement="bottom">
          <div class="right-menu-item hover-effect" @click="openManual">
            <svg-icon icon-class="documentation" />
          </div>
        </el-tooltip>

        <el-tooltip content="全屏" effect="dark" placement="bottom">
          <screenfull id="screenfull" class="right-menu-item hover-effect" />
        </el-tooltip>

        <el-tooltip content="布局大小" effect="dark" placement="bottom">
          <size-select id="size-select" class="right-menu-item hover-effect" />
        </el-tooltip>

        <el-tooltip content="主题设置" effect="dark" placement="bottom">
          <div class="right-menu-item hover-effect" @click="setTheme">
            <el-icon :size="18"><Brush /></el-icon>
          </div>
        </el-tooltip>
      </template>
      <div class="avatar-container">
        <el-dropdown class="right-menu-item hover-effect" trigger="click" @command="handleCommand">
          <div class="avatar-wrapper">
            <img :src="avatarSrc" class="user-avatar" alt="用户头像" @error="handleAvatarError" />
            <el-icon><caret-bottom /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <router-link v-if="!dynamic" to="/user/profile">
                <el-dropdown-item>个人中心</el-dropdown-item>
              </router-link>
              <el-dropdown-item v-if="settingsStore.showSettings" command="setLayout">
                <span>布局设置</span>
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <span>退出登录</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
    <!-- AI 智能助手抽屉 -->
    <AiChatDrawer ref="aiChatDrawerRef" />
  </div>
</template>

<script setup lang="ts">
import SearchMenu from './TopBar/search.vue';
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
import { Brush, ChatDotRound } from '@element-plus/icons-vue';
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
const userId = computed(() => userStore.userId);
const navType = computed(() => themeStore.navType);
const showLogo = computed(() => themeStore.sidebarLogo);

const companyName = ref(undefined);
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
  window.open('https://gitee.com/NexusERP/nexus-erp', '_blank');
};

/** 打开项目文档（public/manual 随前端站点部署，地址需拼接部署上下文路径） */
const openManual = () => {
  window.open(`${import.meta.env.VITE_APP_CONTEXT_PATH || '/'}manual/index.html`, '_blank');
};

// 动态切换
const dynamicTenantEvent = async (tenantId: string) => {
  if (companyName.value != null && companyName.value !== '') {
    await dynamicTenant(tenantId);
    dynamic.value = true;
    await router.push('/');
    await proxy?.$tab.closeAllPage();
    await proxy?.$tab.refreshPage();
  }
};

const dynamicClearEvent = async () => {
  await dynamicClear();
  dynamic.value = false;
  await router.push('/');
  await proxy?.$tab.closeAllPage();
  await proxy?.$tab.refreshPage();
};

/** 租户列表 */
const initTenantList = async () => {
  const { data } = await getTenantList(true);
  tenantEnabled.value = data.tenantEnabled === undefined ? true : data.tenantEnabled;
  if (tenantEnabled.value) {
    tenantList.value = data.voList;
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
// 定义Command方法对象 通过key直接调用方法
const commandMap: { [key: string]: any } = {
  setLayout,
  logout
};
const handleCommand = (command: string) => {
  // 判断是否存在该方法
  if (commandMap[command]) {
    commandMap[command]();
  }
};
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
.navbar.navtop {
  .hamburger-container {
    display: none !important;
  }
}

:deep(.el-select .el-input__wrapper) {
  height: 30px;
}

:deep(.el-badge__content.is-fixed) {
  top: 12px;
}

.flex {
  display: flex;
}

.align-center {
  align-items: center;
}

.navbar {
  height: 50px;
  overflow: hidden;
  position: relative;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-lighter);
  box-shadow: none;
  display: flex;
  align-items: center;
  box-sizing: border-box;

  .hamburger-container {
    line-height: 46px;
    height: 100%;
    cursor: pointer;
    transition: background 0.3s;
    -webkit-tap-highlight-color: transparent;
    display: flex;
    align-items: center;
    flex-shrink: 0;
    margin-right: 8px;

    &:hover {
      background: var(--el-fill-color-lighter);
    }
  }

  .breadcrumb-container {
    flex-shrink: 0;
  }

  .topmenu-container {
    position: absolute;
    left: 50px;
  }

  .topbar-container {
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    overflow: hidden;
    margin-left: 8px;
  }

  .right-menu {
    height: 100%;
    line-height: 50px;
    display: flex;
    align-items: center;
    margin-left: auto;

    &:focus {
      outline: none;
    }

    .right-menu-item {
      display: inline-block;
      padding: 0 8px;
      height: 100%;
      font-size: 18px;
      color: var(--el-text-color-regular);
      vertical-align: text-bottom;

      &.hover-effect {
        cursor: pointer;
        transition: background 0.3s;

        &:hover {
          background: var(--el-fill-color-lighter);
        }
      }
    }

    .avatar-container {
      margin-right: 40px;

      .avatar-wrapper {
        margin-top: 5px;
        position: relative;

        .user-avatar {
          cursor: pointer;
          width: 40px;
          height: 40px;
          border-radius: var(--app-radius-md);
          margin-top: 10px;
        }

        i {
          cursor: pointer;
          position: absolute;
          right: -20px;
          top: 25px;
          font-size: 12px;
        }
      }
    }
  }
}
</style>
