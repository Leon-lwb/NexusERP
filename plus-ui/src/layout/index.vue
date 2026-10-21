<template>
  <div :class="classObj" class="app-wrapper" :style="{ '--current-color': theme }">
    <div v-if="device === 'mobile' && sidebar.opened" class="drawer-bg" @click="handleClickOutside" />
    <side-bar v-if="showSidebar" class="sidebar-container" />
    <div :class="{ hasTagsView: needTagsView, sidebarHide: !showSidebar }" class="main-container">
      <div :class="{ 'fixed-header': fixedHeader }">
        <navbar ref="navbarRef" @set-layout="setLayout" @set-theme="setTheme" />
        <tags-view v-if="needTagsView" />
      </div>
      <app-main />
      <theme-settings ref="themeSettingRef" />
    </div>
  </div>
</template>

<script setup lang="ts">
import SideBar from './components/Sidebar/index.vue';
import { AppMain, Navbar, TagsView, ThemeSettings } from './components';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import { NavTypeEnum } from '@/enums/NavTypeEnum';
import { initWebSocket } from '@/utils/websocket';
import { initSSE } from '@/utils/sse';
import { useImStore } from '@/store/modules/im';
import { usePermissionStore } from '@/store/modules/permission';

const themeStore = useThemeStore();
const theme = computed(() => themeStore.brandColor);
const sidebar = computed(() => useAppStore().sidebar);
const device = computed(() => useAppStore().device);
const needTagsView = computed(() => themeStore.isTabsEnabled);
const fixedHeader = computed(() => themeStore.fixedHeader);
const layout = computed(() => themeStore.navType);

// 根据布局模式判断是否显示侧边栏
const showSidebar = computed(() => {
  if (sidebar.value.hide) return false;
  return device.value === 'mobile' || layout.value === NavTypeEnum.LEFT || layout.value === NavTypeEnum.MIX;
});

const classObj = computed(() => ({
  hideSidebar: !sidebar.value.opened,
  openSidebar: sidebar.value.opened,
  withoutAnimation: sidebar.value.withoutAnimation,
  mobile: device.value === 'mobile'
}));

const { width } = useWindowSize();
const WIDTH = 992; // refer to Bootstrap's responsive design

watchEffect(() => {
  if (device.value === 'mobile') {
    useAppStore().closeSideBar({ withoutAnimation: false });
  }
  if (width.value - 1 < WIDTH) {
    useAppStore().toggleDevice('mobile');
    useAppStore().closeSideBar({ withoutAnimation: true });
  } else {
    useAppStore().toggleDevice('desktop');
  }
});

// 监听导航类型变化，控制侧边栏显示
watch(
  [() => themeStore.navType, device],
  ([newType, newDevice]) => {
    useAppStore().toggleSideBarHide(newType === NavTypeEnum.TOP && newDevice !== 'mobile');
    if (newDevice === 'mobile' || newType !== NavTypeEnum.MIX) {
      const permissionStore = usePermissionStore();
      permissionStore.setSidebarRouters(permissionStore.defaultRoutes as any);
    }
  },
  { immediate: true }
);

const navbarRef = ref<InstanceType<typeof Navbar>>();
const themeSettingRef = ref<InstanceType<typeof ThemeSettings>>();

onMounted(() => {
  nextTick(() => {
    navbarRef.value?.initTenantList();
  });
});

onMounted(() => {
  const protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
  initWebSocket(protocol + window.location.host + import.meta.env.VITE_APP_BASE_API + '/resource/websocket');
});

onMounted(() => {
  initSSE(import.meta.env.VITE_APP_BASE_API + '/resource/sse');
});

onMounted(() => {
  // IM 全局实时监听（会话/未读/通知/标题闪烁），应用级单例
  useImStore().init();
});

const handleClickOutside = () => {
  useAppStore().closeSideBar({ withoutAnimation: false });
};

const setLayout = () => {
  themeSettingRef.value?.open('layout');
};

const setTheme = () => {
  themeSettingRef.value?.open();
};
</script>

<style lang="scss" scoped>
@use '@/assets/styles/mixin.scss';
@use '@/assets/styles/variables.module.scss' as *;

.app-wrapper {
  @include mixin.clearfix;
  position: relative;
  height: 100%;
  width: 100%;

  &.mobile.openSidebar {
    position: fixed;
    top: 0;
  }
}

.drawer-bg {
  background: #000;
  opacity: 0.3;
  width: 100%;
  top: 0;
  height: 100%;
  position: absolute;
  z-index: 999;
}

.fixed-header {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 9;
  width: calc(100% - #{$base-sidebar-width});
  transition: width 0.28s;
  background: $fixed-header-bg;
}

.hideSidebar .fixed-header {
  width: calc(100% - 54px);
}

.sidebarHide .fixed-header {
  width: 100%;
}

.mobile .fixed-header {
  width: 100%;
}
</style>
