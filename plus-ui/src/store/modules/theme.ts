import { defineStore } from 'pinia';
import { useStorage } from '@vueuse/core';
import { ref, computed, watch } from 'vue';
import { ThemeModeEnum, BrandColorEnum, ContentDensityEnum, TabsModeEnum } from '@/enums/ThemeEnum';
import { NavTypeEnum } from '@/enums/NavTypeEnum';
import { SideThemeEnum } from '@/enums/SideThemeEnum';
import { handleThemeStyle } from '@/utils/theme';
import defaultSettings from '@/settings';

export interface ThemeConfig {
  themeMode: ThemeModeEnum;
  brandColor: string;
  sideTheme: SideThemeEnum;
  navType: NavTypeEnum;
  contentDensity: ContentDensityEnum;
  tabsMode: TabsModeEnum;
  tabsIcon: boolean;
  fixedHeader: boolean;
  sidebarLogo: boolean;
  animationEnable: boolean;
}

const defaultThemeConfig: ThemeConfig = {
  themeMode: ThemeModeEnum.LIGHT,
  brandColor: BrandColorEnum.TECH_BLUE,
  sideTheme: SideThemeEnum.DARK,
  navType: NavTypeEnum.LEFT,
  contentDensity: ContentDensityEnum.COMFORTABLE,
  tabsMode: TabsModeEnum.STANDARD,
  tabsIcon: false,
  fixedHeader: true,
  sidebarLogo: true,
  animationEnable: false
};

function migrateFromLegacySettings(): Partial<ThemeConfig> {
  try {
    const raw = localStorage.getItem('layout-setting');
    if (!raw) return {};
    const legacy = JSON.parse(raw);
    const migrated: Partial<ThemeConfig> = {};
    if (legacy.theme) migrated.brandColor = legacy.theme;
    if (legacy.sideTheme) migrated.sideTheme = legacy.sideTheme;
    if (legacy.navType) migrated.navType = legacy.navType;
    if (legacy.tagsView !== undefined) {
      migrated.tabsMode = legacy.tagsView ? TabsModeEnum.STANDARD : TabsModeEnum.OFF;
    }
    if (legacy.tagsIcon !== undefined) migrated.tabsIcon = legacy.tagsIcon;
    if (legacy.fixedHeader !== undefined) migrated.fixedHeader = legacy.fixedHeader;
    if (legacy.sidebarLogo !== undefined) migrated.sidebarLogo = legacy.sidebarLogo;
    if (legacy.animationEnable !== undefined) migrated.animationEnable = legacy.animationEnable;
    if (legacy.dark) migrated.themeMode = ThemeModeEnum.DARK;
    return migrated;
  } catch {
    return {};
  }
}

export const useThemeStore = defineStore('theme', () => {
  const storageConfig = useStorage<ThemeConfig>('theme-config', defaultThemeConfig);

  const migrated = migrateFromLegacySettings();
  const mergedConfig = { ...defaultThemeConfig, ...migrated, ...storageConfig.value };

  const themeMode = ref<ThemeModeEnum>(mergedConfig.themeMode);
  const brandColor = ref<string>(mergedConfig.brandColor);
  const sideTheme = ref<SideThemeEnum>(mergedConfig.sideTheme);
  const navType = ref<NavTypeEnum>(mergedConfig.navType);
  const contentDensity = ref<ContentDensityEnum>(mergedConfig.contentDensity);
  const tabsMode = ref<TabsModeEnum>(mergedConfig.tabsMode);
  const tabsIcon = ref<boolean>(mergedConfig.tabsIcon);
  const fixedHeader = ref<boolean>(mergedConfig.fixedHeader);
  const sidebarLogo = ref<boolean>(mergedConfig.sidebarLogo);
  const animationEnable = ref<boolean>(mergedConfig.animationEnable);

  const isDark = computed(() => themeMode.value === ThemeModeEnum.DARK);
  const isHighContrast = computed(() => themeMode.value === ThemeModeEnum.HIGH_CONTRAST);
  const isCompact = computed(() => contentDensity.value === ContentDensityEnum.COMPACT);
  const isTabsEnabled = computed(() => tabsMode.value !== TabsModeEnum.OFF);

  const brandColorOptions = [
    { label: '科技蓝', value: BrandColorEnum.TECH_BLUE, color: '#409EFF' },
    { label: '商务灰', value: BrandColorEnum.BUSINESS_GRAY, color: '#606266' },
    { label: '护眼绿', value: BrandColorEnum.EYE_CARE_GREEN, color: '#67C23A' },
    { label: '活力橙', value: BrandColorEnum.VIBRANT_ORANGE, color: '#E6A23C' },
    { label: '热情红', value: BrandColorEnum.PASSION_RED, color: '#F56C6C' },
    { label: '优雅紫', value: BrandColorEnum.ELEGANT_PURPLE, color: '#9B59B6' },
    { label: '清新青', value: BrandColorEnum.FRESH_CYAN, color: '#00CED1' }
  ];

  const saveConfig = () => {
    storageConfig.value = {
      themeMode: themeMode.value,
      brandColor: brandColor.value,
      sideTheme: sideTheme.value,
      navType: navType.value,
      contentDensity: contentDensity.value,
      tabsMode: tabsMode.value,
      tabsIcon: tabsIcon.value,
      fixedHeader: fixedHeader.value,
      sidebarLogo: sidebarLogo.value,
      animationEnable: animationEnable.value
    };
  };

  watch(themeMode, (newMode) => {
    const html = document.documentElement;
    html.classList.remove('dark', 'high-contrast');

    switch (newMode) {
      case ThemeModeEnum.DARK:
        html.classList.add('dark');
        sideTheme.value = SideThemeEnum.DARK;
        break;
      case ThemeModeEnum.HIGH_CONTRAST:
        html.classList.add('high-contrast');
        break;
      case ThemeModeEnum.LIGHT:
      default:
        break;
    }

    saveConfig();
  }, { immediate: true });

  watch(brandColor, (newColor) => {
    handleThemeStyle(newColor);
    saveConfig();
  }, { immediate: true });

  watch(contentDensity, (newDensity) => {
    const html = document.documentElement;
    if (newDensity === ContentDensityEnum.COMPACT) {
      html.classList.add('density-compact');
      html.classList.remove('density-comfortable');
    } else {
      html.classList.add('density-comfortable');
      html.classList.remove('density-compact');
    }
    saveConfig();
  }, { immediate: true });

  watch([sideTheme, navType, tabsMode, tabsIcon, fixedHeader, sidebarLogo, animationEnable], () => {
    saveConfig();
  }, { deep: true });

  const setThemeMode = (mode: ThemeModeEnum) => {
    themeMode.value = mode;
  };

  const setBrandColor = (color: string) => {
    brandColor.value = color;
  };

  const setSideTheme = (theme: SideThemeEnum) => {
    if (themeMode.value === ThemeModeEnum.DARK && theme === SideThemeEnum.LIGHT) {
      return;
    }
    sideTheme.value = theme;
  };

  const setNavType = (type: NavTypeEnum) => {
    navType.value = type;
  };

  const setContentDensity = (density: ContentDensityEnum) => {
    contentDensity.value = density;
  };

  const setTabsMode = (mode: TabsModeEnum) => {
    tabsMode.value = mode;
  };

  const toggleTabsIcon = () => {
    tabsIcon.value = !tabsIcon.value;
  };

  const toggleFixedHeader = () => {
    fixedHeader.value = !fixedHeader.value;
  };

  const toggleSidebarLogo = () => {
    sidebarLogo.value = !sidebarLogo.value;
  };

  const toggleAnimation = () => {
    animationEnable.value = !animationEnable.value;
  };

  const resetConfig = () => {
    themeMode.value = defaultThemeConfig.themeMode;
    brandColor.value = defaultThemeConfig.brandColor;
    sideTheme.value = defaultThemeConfig.sideTheme;
    navType.value = defaultThemeConfig.navType;
    contentDensity.value = defaultThemeConfig.contentDensity;
    tabsMode.value = defaultThemeConfig.tabsMode;
    tabsIcon.value = defaultThemeConfig.tabsIcon;
    fixedHeader.value = defaultThemeConfig.fixedHeader;
    sidebarLogo.value = defaultThemeConfig.sidebarLogo;
    animationEnable.value = defaultThemeConfig.animationEnable;
    saveConfig();
  };

  const initTheme = () => {
    handleThemeStyle(brandColor.value);

    const html = document.documentElement;
    html.classList.remove('dark', 'high-contrast');

    switch (themeMode.value) {
      case ThemeModeEnum.DARK:
        html.classList.add('dark');
        break;
      case ThemeModeEnum.HIGH_CONTRAST:
        html.classList.add('high-contrast');
        break;
    }

    if (contentDensity.value === ContentDensityEnum.COMPACT) {
      html.classList.add('density-compact');
    } else {
      html.classList.add('density-comfortable');
    }
  };

  return {
    themeMode,
    brandColor,
    sideTheme,
    navType,
    contentDensity,
    tabsMode,
    tabsIcon,
    fixedHeader,
    sidebarLogo,
    animationEnable,
    isDark,
    isHighContrast,
    isCompact,
    isTabsEnabled,
    brandColorOptions,
    setThemeMode,
    setBrandColor,
    setSideTheme,
    setNavType,
    setContentDensity,
    setTabsMode,
    toggleTabsIcon,
    toggleFixedHeader,
    toggleSidebarLogo,
    toggleAnimation,
    resetConfig,
    initTheme,
    saveConfig
  };
});
