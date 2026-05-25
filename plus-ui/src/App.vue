<template>
  <el-config-provider :locale="appStore.locale" :size="appStore.size">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';

const appStore = useAppStore();
const themeStore = useThemeStore();

onMounted(() => {
  nextTick(() => {
    // 初始化主题系统
    themeStore.initTheme();
  });
});
</script>
