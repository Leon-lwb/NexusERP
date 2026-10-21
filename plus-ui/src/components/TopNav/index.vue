<template>
  <el-menu
    ref="menuRef"
    :popper-class="popupClass"
    :default-active="activeMenu"
    :active-text-color="theme"
    mode="horizontal"
    :ellipsis="true"
    @select="handleSelect"
  >
    <el-menu-item v-for="item in topMenus" :key="item.path" :index="item.path">
      <svg-icon v-if="item.meta?.icon && item.meta.icon !== '#'" :icon-class="item.meta.icon" />
      <span>{{ item.meta?.title }}</span>
    </el-menu-item>
  </el-menu>
</template>

<script setup lang="ts">
import { useHorizontalMenuKeyboard } from '@/composables/useHorizontalMenuKeyboard';
import { constantRoutes } from '@/router';
import { isHttp } from '@/utils/validate';
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import { usePermissionStore } from '@/store/modules/permission';
import { RouteRecordRaw } from 'vue-router';

// 当前激活菜单的 index
const currentIndex = ref<string>();
// 隐藏侧边栏路由
const hideList = ['/index', '/user/profile'];

const appStore = useAppStore();
const themeStore = useThemeStore();
const permissionStore = usePermissionStore();
const { menuRef, popupClass } = useHorizontalMenuKeyboard();
const route = useRoute();
const router = useRouter();

// 主题颜色
const theme = computed(() => themeStore.brandColor);
// 所有的路由信息
const routers = computed(() => permissionStore.getTopbarRoutes());

// 顶部显示菜单
const topMenus = computed(() => {
  const topMenus: RouteRecordRaw[] = [];
  routers.value.map((menu) => {
    if (menu.hidden !== true) {
      // 兼容顶部栏一级菜单内部跳转
      if (menu.path === '/' && menu.children) {
        topMenus.push(menu.children ? menu.children[0] : menu);
      } else {
        topMenus.push(menu);
      }
    }
  });
  return topMenus;
});

// 设置子路由
const childrenMenus = computed(() => {
  const childrenMenus: RouteRecordRaw[] = [];
  routers.value.map((router) => {
    router.children?.forEach((item) => {
      if (item.parentPath === undefined) {
        if (router.path === '/') {
          item.path = '/' + item.path;
        } else {
          if (!isHttp(item.path)) {
            item.path = router.path + '/' + item.path;
          }
        }
        item.parentPath = router.path;
      }
      childrenMenus.push(item);
    });
  });
  return constantRoutes.concat(childrenMenus);
});

// 默认激活的菜单
const activeMenu = computed(() => {
  let path = route.path;
  if (path === '/index') {
    path = '/system/user';
  }
  let activePath = path;
  if (path !== undefined && path.lastIndexOf('/') > 0 && hideList.indexOf(path) === -1) {
    const tmpPath = path.substring(1, path.length);
    if (!route.meta.link) {
      activePath = '/' + tmpPath.substring(0, tmpPath.indexOf('/'));
      appStore.toggleSideBarHide(false);
    }
  } else if (!route.children) {
    activePath = path;
    appStore.toggleSideBarHide(true);
  }
  activeRoutes(activePath);
  return activePath;
});

const handleSelect = (key: string) => {
  currentIndex.value = key;
  const route = routers.value.find((item) => item.path === key);
  if (isHttp(key)) {
    // http(s):// 路径新窗口打开
    window.open(key, '_blank');
  } else if (!route || !route.children) {
    // 没有子路由路径内部打开
    const routeMenu = childrenMenus.value.find((item) => item.path === key);
    if (routeMenu && routeMenu.query) {
      const query = JSON.parse(routeMenu.query);
      router.push({ path: key, query: query });
    } else {
      router.push({ path: key });
    }
    appStore.toggleSideBarHide(true);
  } else {
    // 显示左侧联动菜单
    activeRoutes(key);
    appStore.toggleSideBarHide(false);
  }
};

const activeRoutes = (key: string) => {
  const routes: RouteRecordRaw[] = [];
  if (childrenMenus.value && childrenMenus.value.length > 0) {
    childrenMenus.value.map((item) => {
      if (key == item.parentPath || (key == 'index' && '' == item.path)) {
        routes.push(item);
      }
    });
  }
  if (routes.length > 0) {
    permissionStore.setSidebarRouters(routes);
  } else {
    appStore.toggleSideBarHide(true);
  }
  return routes;
};
</script>

<style lang="scss" scoped>
.el-menu--horizontal {
  --el-menu-horizontal-height: 50px;
  min-width: 0;
  border-bottom: 0;
  :deep([role='menuitem']:focus-visible) {
    outline: 2px solid var(--el-color-primary);
    outline-offset: -3px;
  }
  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    height: 50px;
    line-height: 50px;
    padding: 0 12px;
    color: var(--el-text-color-regular);
  }
  :deep(.el-menu-item.is-active),
  :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
    color: var(--el-color-primary);
  }
  :deep(.svg-icon) {
    margin-right: 6px;
  }
}
</style>
