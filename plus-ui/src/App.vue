<template>
  <el-config-provider :locale="zhCN" :size="appStore.size">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import { useAppStore } from '@/store/modules/app';
import { useThemeStore } from '@/store/modules/theme';
import zhCN from 'element-plus/es/locale/lang/zh-cn';

const appStore = useAppStore();
const themeStore = useThemeStore();

onMounted(() => {
  nextTick(() => {
    // 初始化主题系统
    themeStore.initTheme();
  });
});
</script>
