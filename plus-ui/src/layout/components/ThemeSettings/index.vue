<template>
  <el-drawer v-model="visible" :with-header="false" direction="rtl" size="360px" close-on-click-modal class="theme-settings-drawer">
    <div class="theme-settings-container">
      <!-- 标题 -->
      <div class="settings-header">
        <h3 class="settings-title">
          <el-icon><Setting /></el-icon>
          主题风格设置
        </h3>
        <p class="settings-subtitle">自定义您的界面外观和交互体验</p>
      </div>

      <el-scrollbar class="settings-content">
        <!-- 1. 基础模式切换 -->
        <section class="settings-section">
          <h4 class="section-title">
            <el-icon><Sunny /></el-icon>
            基础模式
          </h4>
          <div class="mode-selector">
            <div
              v-for="mode in themeModes"
              :key="mode.value"
              class="mode-item"
              :class="{ active: themeStore.themeMode === mode.value }"
              @click="handleThemeModeChange(mode.value)"
            >
              <div class="mode-icon" :style="{ background: mode.gradient }">
                <el-icon :size="20"><component :is="mode.icon" /></el-icon>
              </div>
              <span class="mode-label">{{ mode.label }}</span>
              <div v-if="themeStore.themeMode === mode.value" class="mode-check">
                <el-icon><Check /></el-icon>
              </div>
            </div>
          </div>
        </section>

        <el-divider />

        <!-- 2. 品牌色/主题色选择 -->
        <section class="settings-section">
          <h4 class="section-title">
            <el-icon><Brush /></el-icon>
            品牌主题色
          </h4>
          <div class="color-palette">
            <div
              v-for="color in themeStore.brandColorOptions"
              :key="color.value"
              class="color-item"
              :class="{ active: themeStore.brandColor === color.value }"
              @click="handleBrandColorChange(color.value)"
            >
              <div class="color-circle" :style="{ background: color.color }">
                <el-icon v-if="themeStore.brandColor === color.value" color="#fff"><Check /></el-icon>
              </div>
              <span class="color-label">{{ color.label }}</span>
            </div>
          </div>
          <div class="custom-color">
            <span>自定义颜色</span>
            <el-color-picker
              v-model="customColor"
              :predefine="predefineColors"
              @change="handleCustomColorChange"
            />
          </div>
        </section>

        <el-divider />

        <!-- 3. 导航布局与样式 -->
        <section class="settings-section">
          <h4 class="section-title">
            <el-icon><Menu /></el-icon>
            导航布局
          </h4>
          <div class="layout-selector">
            <div
              v-for="layout in navLayouts"
              :key="layout.value"
              class="layout-item"
              :class="{ active: themeStore.navType === layout.value }"
              @click="handleNavTypeChange(layout.value)"
            >
              <div class="layout-preview" :class="layout.class">
                <div class="preview-sidebar"></div>
                <div class="preview-header"></div>
                <div class="preview-content"></div>
              </div>
              <span class="layout-label">{{ layout.label }}</span>
              <div v-if="themeStore.navType === layout.value" class="layout-check">
                <el-icon><Check /></el-icon>
              </div>
            </div>
          </div>

          <!-- 侧边栏主题（仅在非深色模式下显示） -->
          <div v-if="!themeStore.isDark" class="sidebar-theme">
            <span class="setting-label">侧边栏风格</span>
            <div class="theme-toggle">
              <el-radio-group v-model="themeStore.sideTheme" size="small">
                <el-radio-button label="theme-dark">
                  <el-icon><Moon /></el-icon>
                  深色
                </el-radio-button>
                <el-radio-button label="theme-light">
                  <el-icon><Sunny /></el-icon>
                  浅色
                </el-radio-button>
              </el-radio-group>
            </div>
          </div>
        </section>

        <el-divider />

        <!-- 4. 内容密度调节 -->
        <section class="settings-section">
          <h4 class="section-title">
            <el-icon><ScaleToOriginal /></el-icon>
            内容密度
          </h4>
          <div class="density-selector">
            <div
              v-for="density in densityOptions"
              :key="density.value"
              class="density-item"
              :class="{ active: themeStore.contentDensity === density.value }"
              @click="handleDensityChange(density.value)"
            >
              <div class="density-preview" :class="density.class">
                <div class="density-row"></div>
                <div class="density-row"></div>
                <div class="density-row"></div>
              </div>
              <span class="density-label">{{ density.label }}</span>
              <div v-if="themeStore.contentDensity === density.value" class="density-check">
                <el-icon><Check /></el-icon>
              </div>
            </div>
          </div>
        </section>

        <el-divider />

        <!-- 5. 多页签模式 -->
        <section class="settings-section">
          <h4 class="section-title">
            <el-icon><DocumentCopy /></el-icon>
            多页签模式
          </h4>
          <div class="tabs-selector">
            <div
              v-for="tab in tabsModes"
              :key="tab.value"
              class="tabs-item"
              :class="{ active: themeStore.tabsMode === tab.value }"
              @click="handleTabsModeChange(tab.value)"
            >
              <div class="tabs-preview">
                <div v-for="i in tab.previewCount" :key="i" class="tab-preview-item"></div>
              </div>
              <span class="tabs-label">{{ tab.label }}</span>
              <div v-if="themeStore.tabsMode === tab.value" class="tabs-check">
                <el-icon><Check /></el-icon>
              </div>
            </div>
          </div>

          <!-- 页签设置（仅在开启页签时显示） -->
          <div v-if="themeStore.isTabsEnabled" class="tabs-settings">
            <div class="setting-row">
              <span class="setting-label">显示页签图标</span>
              <el-switch v-model="themeStore.tabsIcon" />
            </div>
          </div>
        </section>

        <el-divider />

        <!-- 6. 其他设置 -->
        <section class="settings-section">
          <h4 class="section-title">
            <el-icon><Tools /></el-icon>
            其他设置
          </h4>
          <div class="other-settings">
            <div class="setting-row">
              <span class="setting-label">
                <el-icon><Lock /></el-icon>
                固定 Header
              </span>
              <el-switch v-model="themeStore.fixedHeader" />
            </div>
            <div class="setting-row">
              <span class="setting-label">
                <el-icon><Picture /></el-icon>
                显示 Logo
              </span>
              <el-switch v-model="themeStore.sidebarLogo" />
            </div>
            <div class="setting-row">
              <span class="setting-label">
                <el-icon><VideoPlay /></el-icon>
                开启动画
              </span>
              <el-switch v-model="themeStore.animationEnable" />
            </div>
          </div>
        </section>
      </el-scrollbar>

      <!-- 底部操作按钮 -->
      <div class="settings-footer">
        <el-button type="primary" @click="saveSettings">
          <el-icon><DocumentChecked /></el-icon>
          保存配置
        </el-button>
        <el-button @click="resetSettings">
          <el-icon><Refresh /></el-icon>
          重置
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useThemeStore } from '@/store/modules/theme';
import { ThemeModeEnum, ContentDensityEnum, TabsModeEnum } from '@/enums/ThemeEnum';
import { NavTypeEnum } from '@/enums/NavTypeEnum';
import { SideThemeEnum } from '@/enums/SideThemeEnum';
import {
  Setting, Sunny, Moon, Brush, Menu, Check,
  ScaleToOriginal, DocumentCopy, Tools, Lock,
  Picture, VideoPlay, DocumentChecked, Refresh, View
} from '@element-plus/icons-vue';

const themeStore = useThemeStore();
const visible = ref(false);
const customColor = ref(themeStore.brandColor);

// 预定义颜色
const predefineColors = [
  '#409EFF', '#67C23A', '#E6A23C', '#F56C6C',
  '#9B59B6', '#00CED1', '#1E90FF', '#FF69B4'
];

// 主题模式选项
const themeModes = [
  {
    value: ThemeModeEnum.LIGHT,
    label: '浅色模式',
    icon: Sunny,
    gradient: 'linear-gradient(135deg, #ffffff 0%, #f5f7fa 100%)'
  },
  {
    value: ThemeModeEnum.DARK,
    label: '深色模式',
    icon: Moon,
    gradient: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 100%)'
  },
  {
    value: ThemeModeEnum.HIGH_CONTRAST,
    label: '高对比度',
    icon: View,
    gradient: 'linear-gradient(135deg, #000000 0%, #ffffff 100%)'
  }
];

// 导航布局选项
const navLayouts = [
  {
    value: NavTypeEnum.LEFT,
    label: '侧边导航',
    class: 'left-layout'
  },
  {
    value: NavTypeEnum.TOP,
    label: '顶部导航',
    class: 'top-layout'
  },
  {
    value: NavTypeEnum.MIX,
    label: '混合导航',
    class: 'mix-layout'
  }
];

// 内容密度选项
const densityOptions = [
  {
    value: ContentDensityEnum.COMPACT,
    label: '紧凑',
    class: 'compact'
  },
  {
    value: ContentDensityEnum.COMFORTABLE,
    label: '舒适',
    class: 'comfortable'
  }
];

// 多页签模式选项
const tabsModes = [
  {
    value: TabsModeEnum.OFF,
    label: '关闭',
    previewCount: 0
  },
  {
    value: TabsModeEnum.STANDARD,
    label: '标准',
    previewCount: 3
  },
  {
    value: TabsModeEnum.SMART,
    label: '智能',
    previewCount: 3
  }
];

// 处理主题模式切换
const handleThemeModeChange = (mode: ThemeModeEnum) => {
  themeStore.setThemeMode(mode);
};

// 处理品牌色切换
const handleBrandColorChange = (color: string) => {
  themeStore.setBrandColor(color);
  customColor.value = color;
};

// 处理自定义颜色
const handleCustomColorChange = (color: string | null) => {
  if (color) {
    themeStore.setBrandColor(color);
  }
};

// 处理导航类型切换
const handleNavTypeChange = (type: NavTypeEnum) => {
  themeStore.setNavType(type);
};

// 处理内容密度切换
const handleDensityChange = (density: ContentDensityEnum) => {
  themeStore.setContentDensity(density);
};

// 处理多页签模式切换
const handleTabsModeChange = (mode: TabsModeEnum) => {
  themeStore.setTabsMode(mode);
};

// 保存设置
const saveSettings = () => {
  themeStore.saveConfig();
  ElMessage.success('主题配置已保存');
  visible.value = false;
};

// 重置设置
const resetSettings = () => {
  ElMessageBox.confirm('确定要重置所有主题设置吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    themeStore.resetConfig();
    customColor.value = themeStore.brandColor;
    ElMessage.success('主题设置已重置');
  }).catch(() => {});
};

// 打开设置面板
const open = () => {
  visible.value = true;
  customColor.value = themeStore.brandColor;
};

// 关闭设置面板
const close = () => {
  visible.value = false;
};

defineExpose({
  open,
  close
});
</script>

<style lang="scss" scoped>
.theme-settings-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.settings-header {
  padding: 20px 20px 0;
  border-bottom: 1px solid var(--el-border-color-light);

  .settings-title {
    font-size: 18px;
    font-weight: 600;
    margin: 0 0 8px;
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--el-text-color-primary);
  }

  .settings-subtitle {
    font-size: 13px;
    color: var(--el-text-color-secondary);
    margin: 0 0 16px;
  }
}

.settings-content {
  flex: 1;
  padding: 0 20px;
}

.settings-section {
  padding: 16px 0;

  .section-title {
    font-size: 14px;
    font-weight: 600;
    margin: 0 0 16px;
    display: flex;
    align-items: center;
    gap: 8px;
    color: var(--el-text-color-primary);
  }
}

// 基础模式选择器
.mode-selector {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.mode-item {
  position: relative;
  cursor: pointer;
  text-align: center;
  padding: 12px 8px;
  border-radius: var(--app-radius-md);
  border: 2px solid transparent;
  transition: all 0.3s ease;
  background: var(--el-bg-color-overlay);

  &:hover {
    border-color: var(--el-border-color);
    transform: translateY(-2px);
  }

  &.active {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }

  .mode-icon {
    width: 44px;
    height: 44px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    margin: 0 auto 8px;
    color: var(--el-text-color-primary);
    box-shadow: var(--app-shadow-sm);
  }

  .mode-label {
    font-size: 12px;
    color: var(--el-text-color-primary);
  }

  .mode-check {
    position: absolute;
    top: 4px;
    right: 4px;
    width: 18px;
    height: 18px;
    background: var(--el-color-primary);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 12px;
  }
}

// 品牌色选择器
.color-palette {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.color-item {
  cursor: pointer;
  text-align: center;

  .color-circle {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    margin: 0 auto 6px;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: all 0.3s ease;
    box-shadow: var(--app-shadow-sm);

    &:hover {
      transform: scale(1.1);
    }
  }

  &.active .color-circle {
    box-shadow: 0 0 0 3px var(--el-color-primary-light-5);
  }

  .color-label {
    font-size: 11px;
    color: var(--el-text-color-secondary);
  }
}

.custom-color {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: var(--app-radius-md);

  span {
    font-size: 13px;
    color: var(--el-text-color-primary);
  }
}

// 导航布局选择器
.layout-selector {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.layout-item {
  position: relative;
  cursor: pointer;
  text-align: center;
  padding: 12px;
  border-radius: var(--app-radius-md);
  border: 2px solid transparent;
  transition: all 0.3s ease;
  background: var(--el-bg-color-overlay);

  &:hover {
    border-color: var(--el-border-color);
  }

  &.active {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }

  .layout-preview {
    width: 56px;
    height: 40px;
    margin: 0 auto 8px;
    background: #f0f2f5;
    border-radius: 4px;
    position: relative;
    overflow: hidden;

    .preview-sidebar {
      position: absolute;
      background: #1b2a47;
    }

    .preview-header {
      position: absolute;
      background: #1b2a47;
    }

    .preview-content {
      position: absolute;
      background: #fff;
    }

    &.left-layout {
      .preview-sidebar {
        left: 0;
        top: 0;
        width: 30%;
        height: 100%;
      }
      .preview-content {
        right: 0;
        top: 0;
        width: 70%;
        height: 100%;
      }
    }

    &.top-layout {
      .preview-header {
        left: 0;
        top: 0;
        width: 100%;
        height: 30%;
      }
      .preview-content {
        left: 0;
        bottom: 0;
        width: 100%;
        height: 70%;
      }
    }

    &.mix-layout {
      .preview-header {
        left: 0;
        top: 0;
        width: 100%;
        height: 25%;
      }
      .preview-sidebar {
        left: 0;
        top: 25%;
        width: 25%;
        height: 75%;
      }
      .preview-content {
        right: 0;
        bottom: 0;
        width: 75%;
        height: 75%;
      }
    }
  }

  .layout-label {
    font-size: 12px;
    color: var(--el-text-color-primary);
  }

  .layout-check {
    position: absolute;
    top: 4px;
    right: 4px;
    width: 18px;
    height: 18px;
    background: var(--el-color-primary);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 12px;
  }
}

.sidebar-theme {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: var(--app-radius-md);

  .setting-label {
    font-size: 13px;
    color: var(--el-text-color-primary);
  }
}

// 内容密度选择器
.density-selector {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.density-item {
  position: relative;
  cursor: pointer;
  text-align: center;
  padding: 12px;
  border-radius: var(--app-radius-md);
  border: 2px solid transparent;
  transition: all 0.3s ease;
  background: var(--el-bg-color-overlay);

  &:hover {
    border-color: var(--el-border-color);
  }

  &.active {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }

  .density-preview {
    width: 60px;
    height: 40px;
    margin: 0 auto 8px;
    background: #f0f2f5;
    border-radius: 4px;
    padding: 4px;
    display: flex;
    flex-direction: column;
    gap: 2px;

    .density-row {
      background: #d9d9d9;
      border-radius: 2px;
      flex: 1;
    }

    &.compact {
      gap: 1px;
      .density-row {
        border-radius: 1px;
      }
    }

    &.comfortable {
      gap: 4px;
    }
  }

  .density-label {
    font-size: 12px;
    color: var(--el-text-color-primary);
  }

  .density-check {
    position: absolute;
    top: 4px;
    right: 4px;
    width: 18px;
    height: 18px;
    background: var(--el-color-primary);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 12px;
  }
}

// 多页签选择器
.tabs-selector {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.tabs-item {
  position: relative;
  cursor: pointer;
  text-align: center;
  padding: 12px;
  border-radius: var(--app-radius-md);
  border: 2px solid transparent;
  transition: all 0.3s ease;
  background: var(--el-bg-color-overlay);

  &:hover {
    border-color: var(--el-border-color);
  }

  &.active {
    border-color: var(--el-color-primary);
    background: var(--el-color-primary-light-9);
  }

  .tabs-preview {
    width: 60px;
    height: 24px;
    margin: 0 auto 8px;
    background: #f0f2f5;
    border-radius: 4px;
    display: flex;
    padding: 2px;
    gap: 2px;

    .tab-preview-item {
      flex: 1;
      background: #d9d9d9;
      border-radius: 2px;

      &:first-child {
        background: var(--el-color-primary);
      }
    }
  }

  .tabs-label {
    font-size: 12px;
    color: var(--el-text-color-primary);
  }

  .tabs-check {
    position: absolute;
    top: 4px;
    right: 4px;
    width: 18px;
    height: 18px;
    background: var(--el-color-primary);
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #fff;
    font-size: 12px;
  }
}

.tabs-settings {
  margin-top: 12px;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: var(--app-radius-md);
}

// 其他设置
.other-settings {
  .setting-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 10px 0;
    border-bottom: 1px solid var(--el-border-color-lighter);

    &:last-child {
      border-bottom: none;
    }

    .setting-label {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 13px;
      color: var(--el-text-color-primary);
    }
  }
}

// 底部按钮
.settings-footer {
  padding: 16px 20px;
  border-top: 1px solid var(--el-border-color-light);
  display: flex;
  gap: 12px;

  .el-button {
    flex: 1;
  }
}

// 通用设置行样式
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.setting-label {
  font-size: 13px;
  color: var(--el-text-color-primary);
}
</style>
