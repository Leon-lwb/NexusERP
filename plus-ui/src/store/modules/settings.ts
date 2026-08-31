import { defineStore } from 'pinia';
import defaultSettings from '@/settings';
import { useDynamicTitle } from '@/utils/dynamicTitle';
import { useStorage } from '@vueuse/core';
import { ref } from 'vue';

export const useSettingsStore = defineStore('setting', () => {
  // 动态标题开关单独持久化（主题相关字段已迁移至 useThemeStore）
  const dynamicTitle = useStorage<boolean>('dynamic-title', defaultSettings.dynamicTitle);
  const title = ref<string>(defaultSettings.title);
  const showSettings = ref<boolean>(defaultSettings.showSettings);

  const setTitle = (value: string) => {
    title.value = value;
    useDynamicTitle();
  };
  return {
    title,
    showSettings,
    dynamicTitle,
    setTitle
  };
});
