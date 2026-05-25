/**
 * 主题模式枚举
 */
export enum ThemeModeEnum {
  /**
   * 浅色/白天模式
   */
  LIGHT = 'light',
  /**
   * 深色/夜间模式
   */
  DARK = 'dark',
  /**
   * 高对比度模式（无障碍）
   */
  HIGH_CONTRAST = 'high-contrast'
}

/**
 * 品牌色/主题色枚举
 */
export enum BrandColorEnum {
  /**
   * 科技蓝
   */
  TECH_BLUE = '#409EFF',
  /**
   * 商务灰
   */
  BUSINESS_GRAY = '#606266',
  /**
   * 极简白
   */
  MINIMAL_WHITE = '#FFFFFF',
  /**
   * 护眼绿
   */
  EYE_CARE_GREEN = '#67C23A',
  /**
   * 活力橙
   */
  VIBRANT_ORANGE = '#E6A23C',
  /**
   * 热情红
   */
  PASSION_RED = '#F56C6C',
  /**
   * 优雅紫
   */
  ELEGANT_PURPLE = '#9B59B6',
  /**
   * 清新青
   */
  FRESH_CYAN = '#00CED1'
}

/**
 * 内容密度枚举
 */
export enum ContentDensityEnum {
  /**
   * 紧凑模式
   */
  COMPACT = 'compact',
  /**
   * 舒适模式
   */
  COMFORTABLE = 'comfortable'
}

/**
 * 多页签模式枚举
 */
export enum TabsModeEnum {
  /**
   * 关闭页签
   */
  OFF = 'off',
  /**
   * 标准页签
   */
  STANDARD = 'standard',
  /**
   * 智能页签（自动清理）
   */
  SMART = 'smart'
}
