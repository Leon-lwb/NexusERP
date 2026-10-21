<template>
  <el-drawer v-model="visible" title="主题与布局" :with-header="false" direction="rtl" size="min(420px, 100vw)" class="theme-settings-drawer">
    <div class="theme-settings">
      <header class="settings-header">
        <div>
          <span class="settings-eyebrow">个性化工作空间</span>
          <h2>主题与布局</h2>
          <p>调整外观，让日常工作更顺手。</p>
        </div>
        <button type="button" class="settings-close" aria-label="关闭主题设置" @click="close">
          <svg-icon icon-class="nav-close" />
        </button>
      </header>

      <el-tabs v-model="activeSection" stretch class="settings-tabs">
        <el-tab-pane label="外观" name="appearance">
          <div class="settings-pane">
            <section class="settings-section" aria-labelledby="theme-mode-title">
              <div class="section-heading">
                <h3 id="theme-mode-title">界面模式</h3>
                <p>选择适合当前环境的显示方式</p>
              </div>
              <div class="choice-grid three-columns">
                <button
                  v-for="mode in themeModes"
                  :key="mode.value"
                  type="button"
                  class="choice-card mode-card"
                  :class="{ 'is-selected': themeStore.themeMode === mode.value }"
                  :aria-pressed="themeStore.themeMode === mode.value"
                  @click="themeStore.setThemeMode(mode.value)"
                >
                  <span class="mode-preview" :class="mode.value" aria-hidden="true"><svg-icon :icon-class="mode.icon" /></span>
                  <span>{{ mode.label }}</span>
                  <span v-if="themeStore.themeMode === mode.value" class="selection-mark" aria-hidden="true">✓</span>
                </button>
              </div>
            </section>

            <section class="settings-section" aria-labelledby="brand-color-title">
              <div class="section-heading">
                <h3 id="brand-color-title">主题颜色</h3>
                <p>用于按钮、选中状态与重点信息</p>
              </div>
              <div class="color-options">
                <button
                  v-for="color in themeStore.brandColorOptions"
                  :key="color.value"
                  type="button"
                  class="color-option"
                  :class="{ 'is-selected': themeStore.brandColor.toLowerCase() === color.value.toLowerCase() }"
                  :aria-pressed="themeStore.brandColor.toLowerCase() === color.value.toLowerCase()"
                  :aria-label="color.label"
                  @click="themeStore.setBrandColor(color.value)"
                >
                  <span class="color-swatch" :style="{ backgroundColor: color.color }" aria-hidden="true"></span>
                  <span>{{ color.label }}</span>
                  <span v-if="themeStore.brandColor.toLowerCase() === color.value.toLowerCase()" class="color-check" aria-hidden="true">✓</span>
                </button>
              </div>
              <div class="setting-row custom-color-row">
                <div>
                  <label for="custom-theme-color" class="setting-label">自定义颜色</label
                  ><span class="setting-description color-value">{{ themeStore.brandColor.toUpperCase() }}</span>
                </div>
                <el-color-picker
                  id="custom-theme-color"
                  :model-value="themeStore.brandColor"
                  :predefine="predefineColors"
                  color-format="hex"
                  aria-label="自定义主题颜色"
                  @change="handleCustomColorChange"
                />
              </div>
            </section>

            <section class="settings-section" aria-labelledby="sidebar-theme-title">
              <div class="section-heading">
                <h3 id="sidebar-theme-title">侧栏风格</h3>
                <p>{{ themeStore.isDark ? '深色模式下，侧栏保持深色以统一外观' : '为导航选择独立的明暗风格' }}</p>
              </div>
              <el-radio-group :model-value="themeStore.sideTheme" :disabled="themeStore.isDark" aria-label="侧栏风格" @change="handleSideThemeChange">
                <el-radio-button :value="SideThemeEnum.DARK">深色侧栏</el-radio-button>
                <el-radio-button :value="SideThemeEnum.LIGHT">浅色侧栏</el-radio-button>
              </el-radio-group>
            </section>
          </div>
        </el-tab-pane>

        <el-tab-pane label="布局" name="layout">
          <div class="settings-pane">
            <section class="settings-section" aria-labelledby="navigation-layout-title">
              <div class="section-heading">
                <h3 id="navigation-layout-title">导航布局</h3>
                <p>按使用习惯安排菜单位置</p>
              </div>
              <div class="choice-grid three-columns">
                <button
                  v-for="layout in navLayouts"
                  :key="layout.value"
                  type="button"
                  class="choice-card"
                  :class="{ 'is-selected': themeStore.navType === layout.value }"
                  :aria-pressed="themeStore.navType === layout.value"
                  @click="themeStore.setNavType(layout.value)"
                >
                  <span class="layout-preview" :class="layout.value" aria-hidden="true"
                    ><span class="preview-header"></span><span class="preview-sidebar"></span><span class="preview-content"></span
                  ></span>
                  <span>{{ layout.label }}</span>
                  <span v-if="themeStore.navType === layout.value" class="selection-mark" aria-hidden="true">✓</span>
                </button>
              </div>
            </section>

            <section class="settings-section" aria-labelledby="content-density-title">
              <div class="section-heading">
                <h3 id="content-density-title">内容密度</h3>
                <p>调整列表行高与页面间距</p>
              </div>
              <div class="choice-grid two-columns">
                <button
                  v-for="density in densityOptions"
                  :key="density.value"
                  type="button"
                  class="choice-card density-card"
                  :class="{ 'is-selected': themeStore.contentDensity === density.value }"
                  :aria-pressed="themeStore.contentDensity === density.value"
                  @click="themeStore.setContentDensity(density.value)"
                >
                  <span class="density-preview" :class="density.value" aria-hidden="true"><span></span><span></span><span></span></span>
                  <span>{{ density.label }}</span>
                  <span class="choice-description">{{ density.description }}</span>
                </button>
              </div>
              <div class="setting-row">
                <div>
                  <span class="setting-label" id="component-size-label">组件尺寸</span><span class="setting-description">调整按钮和输入框大小</span>
                </div>
                <el-radio-group :model-value="appStore.size" size="small" aria-labelledby="component-size-label" @change="handleSizeChange">
                  <el-radio-button value="small">小</el-radio-button><el-radio-button value="default">默认</el-radio-button
                  ><el-radio-button value="large">大</el-radio-button>
                </el-radio-group>
              </div>
            </section>

            <section class="settings-section" aria-labelledby="tabs-mode-title">
              <div class="section-heading">
                <h3 id="tabs-mode-title">页面标签</h3>
                <p>管理同时打开的工作页面</p>
              </div>
              <el-radio-group :model-value="themeStore.tabsMode" aria-label="页面标签模式" @change="handleTabsModeChange">
                <el-radio-button :value="TabsModeEnum.OFF">关闭</el-radio-button><el-radio-button :value="TabsModeEnum.STANDARD">标准</el-radio-button
                ><el-radio-button :value="TabsModeEnum.SMART">智能</el-radio-button>
              </el-radio-group>
              <div class="setting-row">
                <label for="setting-tabs-icon" class="setting-label">显示标签图标</label>
                <el-switch id="setting-tabs-icon" v-model="themeStore.tabsIcon" :disabled="!themeStore.isTabsEnabled" aria-label="显示标签图标" />
              </div>
            </section>
          </div>
        </el-tab-pane>

        <el-tab-pane label="偏好" name="preferences">
          <div class="settings-pane">
            <section class="settings-section" aria-labelledby="interface-preferences-title">
              <div class="section-heading">
                <h3 id="interface-preferences-title">界面偏好</h3>
                <p>让工作区符合你的日常使用习惯</p>
              </div>
              <div v-for="option in displayOptions" :key="option.key" class="setting-row">
                <div>
                  <label :for="'setting-' + option.key" class="setting-label">{{ option.label }}</label
                  ><span class="setting-description">{{ option.description }}</span>
                </div>
                <el-switch :id="'setting-' + option.key" v-model="themeStore[option.key]" :aria-label="option.label" />
              </div>
              <div class="setting-row">
                <div>
                  <label for="setting-dynamic-title" class="setting-label">动态页面标题</label
                  ><span class="setting-description">在浏览器标签中显示当前页面名称</span>
                </div>
                <el-switch id="setting-dynamic-title" v-model="settingsStore.dynamicTitle" aria-label="动态页面标题" @change="useDynamicTitle" />
              </div>
            </section>
          </div>
        </el-tab-pane>
      </el-tabs>

      <footer class="settings-footer">
        <p><span class="saved-indicator" aria-hidden="true"></span>设置即时生效，自动保存于当前浏览器</p>
        <div class="footer-actions">
          <el-button @click="resetSettings">恢复默认</el-button><el-button type="primary" @click="close">完成</el-button>
        </div>
      </footer>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useThemeStore } from '@/store/modules/theme';
import { useSettingsStore } from '@/store/modules/settings';
import { useAppStore } from '@/store/modules/app';
import { useDynamicTitle } from '@/utils/dynamicTitle';
import { ThemeModeEnum, ContentDensityEnum, TabsModeEnum } from '@/enums/ThemeEnum';
import { NavTypeEnum } from '@/enums/NavTypeEnum';
import { SideThemeEnum } from '@/enums/SideThemeEnum';
import defaultSettings from '@/settings';

type SettingsSection = 'appearance' | 'layout' | 'preferences';
const themeStore = useThemeStore();
const settingsStore = useSettingsStore();
const appStore = useAppStore();
const visible = ref(false);
const activeSection = ref<SettingsSection>('appearance');
const predefineColors = themeStore.brandColorOptions.map((color) => color.value);
const themeModes = [
  { value: ThemeModeEnum.LIGHT, label: '浅色', icon: 'nav-sun' },
  { value: ThemeModeEnum.DARK, label: '深色', icon: 'nav-moon' },
  { value: ThemeModeEnum.HIGH_CONTRAST, label: '高对比度', icon: 'nav-contrast' }
];
const navLayouts = [
  { value: NavTypeEnum.LEFT, label: '侧边导航' },
  { value: NavTypeEnum.MIX, label: '混合导航' },
  { value: NavTypeEnum.TOP, label: '顶部导航' }
];
const densityOptions = [
  { value: ContentDensityEnum.COMFORTABLE, label: '舒适', description: '留白清晰，轻松阅读' },
  { value: ContentDensityEnum.COMPACT, label: '紧凑', description: '呈现更多列表信息' }
];
const displayOptions = [
  { key: 'fixedHeader', label: '固定顶部导航', description: '滚动页面时保持导航可见' },
  { key: 'sidebarLogo', label: '显示品牌标识', description: '在导航区域显示系统 Logo' },
  { key: 'animationEnable', label: '页面切换动画', description: '在页面之间切换时显示过渡效果' }
] as const;

/** 自定义颜色直接写入全局状态，清空选择时保留当前有效主题。 */
const handleCustomColorChange = (color: string | null) => {
  if (color) themeStore.setBrandColor(color);
};
const handleSideThemeChange = (value: string | number | boolean | undefined) => {
  themeStore.setSideTheme(value as SideThemeEnum);
};
const handleTabsModeChange = (value: string | number | boolean | undefined) => {
  themeStore.setTabsMode(value as TabsModeEnum);
};
const handleSizeChange = (value: string | number | boolean | undefined) => {
  appStore.setSize(value as 'small' | 'default' | 'large');
};

/** 恢复所有界面偏好；取消确认时不修改状态，不刷新当前工作页面。 */
const resetSettings = async () => {
  try {
    await ElMessageBox.confirm('将恢复外观、布局与偏好的默认设置，是否继续？', '恢复默认设置', {
      confirmButtonText: '恢复默认',
      cancelButtonText: '取消',
      type: 'warning'
    });
  } catch {
    return;
  }
  themeStore.resetConfig();
  appStore.setSize(defaultSettings.size);
  settingsStore.dynamicTitle = defaultSettings.dynamicTitle;
  useDynamicTitle();
  ElMessage.success('已恢复默认设置');
};

/** 两个历史入口使用同一抽屉，并定位到对应设置分组。 */
const open = (section: SettingsSection = 'appearance') => {
  activeSection.value = section;
  visible.value = true;
};
const close = () => {
  visible.value = false;
};
defineExpose({ open, close });
</script>

<style lang="scss" scoped>
.theme-settings {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  color: var(--el-text-color-primary);
}
.settings-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 28px 24px 20px;
  gap: 16px;
}
.settings-eyebrow {
  display: block;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 8px;
  letter-spacing: 1px;
}
.settings-header h2 {
  margin: 0;
  font-size: 22px;
  line-height: 1.4;
  font-weight: 650;
}
.settings-header p {
  margin: 8px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.settings-close {
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  padding: 8px;
  border: 0;
  border-radius: var(--app-radius-sm);
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  font-size: 20px;
}
.settings-close:hover {
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}
.settings-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.settings-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 24px;
  flex-shrink: 0;
}
.settings-tabs :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}
.settings-tabs :deep(.el-tabs__item) {
  height: 44px;
  font-weight: 500;
}
.settings-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
}
.settings-pane {
  padding: 4px 24px 16px;
}
.settings-section {
  padding: 24px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.settings-section:last-child {
  border-bottom: 0;
}
.section-heading {
  margin-bottom: 16px;
}
.section-heading h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
}
.section-heading p {
  margin: 5px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
.choice-grid {
  display: grid;
  gap: 10px;
}
.three-columns {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.two-columns {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.choice-card {
  position: relative;
  appearance: none;
  display: flex;
  align-items: center;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
  min-width: 0;
  min-height: 100px;
  padding: 14px 8px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--app-radius-md);
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  transition:
    border-color 0.16s,
    background-color 0.16s;
}
.choice-card:hover {
  border-color: var(--el-color-primary-light-3);
}
.choice-card.is-selected {
  border-color: var(--el-color-primary);
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}
.selection-mark {
  position: absolute;
  right: 7px;
  top: 5px;
  font-weight: 700;
}
.mode-preview {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  font-size: 23px;
  border: 1px solid var(--el-border-color-lighter);
}
.mode-preview.light {
  background: #f5f7fa;
  color: #475569;
}
.mode-preview.dark {
  background: #202735;
  color: #e2e8f0;
}
.mode-preview.high-contrast {
  background: #000;
  color: #fff;
}
.color-options {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}
.color-option {
  position: relative;
  appearance: none;
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 8px;
  min-height: 70px;
  padding: 10px 4px;
  border: 1px solid transparent;
  border-radius: var(--app-radius-sm);
  background: transparent;
  color: var(--el-text-color-regular);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.color-option:hover {
  background: var(--el-fill-color-light);
}
.color-option.is-selected {
  background: var(--el-fill-color-light);
  border-color: var(--el-color-primary);
}
.color-swatch {
  display: block;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 1px solid var(--el-border-color-lighter);
}
.color-check {
  position: absolute;
  right: 7px;
  top: 3px;
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 700;
}
.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 18px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.setting-row:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}
.setting-label {
  display: block;
  color: var(--el-text-color-primary);
  font-size: 13px;
  line-height: 1.5;
}
.setting-description {
  display: block;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.color-value {
  font-variant-numeric: tabular-nums;
}
.custom-color-row {
  margin-top: 12px;
  padding: 12px;
  background: var(--el-fill-color-light);
  border-radius: var(--app-radius-sm);
}
.custom-color-row:last-child {
  padding-bottom: 12px;
}
.layout-preview {
  position: relative;
  display: block;
  width: 68px;
  height: 42px;
  border: 1px solid var(--el-border-color);
  border-radius: 5px;
  overflow: hidden;
  background: var(--el-fill-color-light);
}
.preview-header {
  position: absolute;
  left: 14px;
  right: 0;
  top: 0;
  height: 9px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color);
}
.preview-sidebar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 14px;
  background: var(--el-color-primary);
  opacity: 0.75;
}
.preview-content {
  position: absolute;
  left: 21px;
  right: 7px;
  top: 16px;
  bottom: 7px;
  border-radius: 2px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
}
.layout-preview.top .preview-header,
.layout-preview.mix .preview-header {
  left: 0;
  background: var(--el-color-primary);
}
.layout-preview.top .preview-sidebar {
  display: none;
}
.layout-preview.top .preview-content {
  left: 7px;
}
.layout-preview.mix .preview-sidebar {
  top: 9px;
  background: var(--el-border-color);
}
.density-preview {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
  width: 74px;
  height: 38px;
  padding: 5px;
  box-sizing: border-box;
  border-radius: 4px;
  border: 1px solid var(--el-border-color);
  background: var(--el-bg-color);
}
.density-preview span {
  display: block;
  height: 5px;
  border-radius: 2px;
  background: var(--el-border-color);
}
.density-preview.compact {
  gap: 3px;
}
.choice-description {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.settings-footer {
  flex-shrink: 0;
  padding: 16px 24px max(20px, env(safe-area-inset-bottom));
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}
.settings-footer p {
  display: flex;
  gap: 7px;
  align-items: center;
  margin: 0 0 14px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary);
}
.saved-indicator {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--el-color-success);
}
.footer-actions {
  display: flex;
  gap: 12px;
}
.footer-actions .el-button {
  flex: 1;
  margin: 0;
  height: 38px;
}
.settings-close:focus-visible,
.choice-card:focus-visible,
.color-option:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 3px;
}
@media (max-width: 420px) {
  .settings-header {
    padding: 24px 20px 16px;
  }
  .settings-pane {
    padding-left: 20px;
    padding-right: 20px;
  }
  .settings-tabs :deep(.el-tabs__header) {
    padding: 0 20px;
  }
  .settings-close {
    width: 44px;
    height: 44px;
  }
  .settings-footer {
    padding-left: 20px;
    padding-right: 20px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .choice-card {
    transition: none;
  }
}
</style>

<style lang="scss">
.theme-settings-drawer {
  --el-drawer-padding-primary: 0px;
}
.theme-settings-drawer .el-drawer__body {
  padding: 0;
  overflow: hidden;
}
</style>
