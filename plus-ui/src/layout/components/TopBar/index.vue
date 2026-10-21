<template>
  <el-menu
    ref="menuRef"
    :popper-class="popupClass"
    class="topbar-menu"
    :ellipsis="true"
    :default-active="activeMenu"
    :active-text-color="theme"
    mode="horizontal"
  >
    <sidebar-item v-for="item in visibleRoutes" :key="item.path" :item="item" :base-path="item.path" />
  </el-menu>
</template>

<script setup lang="ts">
import { useHorizontalMenuKeyboard } from '@/composables/useHorizontalMenuKeyboard';
import SidebarItem from '../Sidebar/SidebarItem.vue';
import { useThemeStore } from '@/store/modules/theme';
import { usePermissionStore } from '@/store/modules/permission';

const { menuRef, popupClass } = useHorizontalMenuKeyboard();
const route = useRoute();
const themeStore = useThemeStore();
const permissionStore = usePermissionStore();
const theme = computed(() => themeStore.brandColor);
const activeMenu = computed(() => route.meta.activeMenu || route.path);
/** 菜单按真实容器宽度折叠，由 Element Plus 维护溢出项和键盘操作。 */
const visibleRoutes = computed(() => permissionStore.getSidebarRoutes().filter((item) => !item.hidden));
</script>

<style lang="scss" scoped>
.topbar-menu {
  --el-menu-horizontal-height: 50px;
  width: 100%;
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
    color: var(--el-text-color-regular);
  }
  :deep(.el-menu-item.is-active),
  :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
    color: var(--el-color-primary);
  }
}
</style>
