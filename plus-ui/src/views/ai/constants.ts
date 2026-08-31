/**
 * AI 模块页面本地常量
 * <p>
 * 业务枚举已统一迁入系统字典(sys_dict_type/sys_dict_data, 2026-08-28 AI 迁移),
 * 此处仅保留纯 UI 配置: 对话模式选项与图片生成尺寸(设计确认不进字典)。
 * </p>
 */

/** 本地枚举选项（label + value + el-tag 类型） */
export interface AiOption {
  /** 中文标签（下拉/单选展示） */
  label: string;
  /** 枚举值 */
  value: string;
  /** el-tag 类型（未指定时按默认标签渲染） */
  elTagType?: ElTagType;
  /** 表格标签展示文案（缺省与 label 一致） */
  tagLabel?: string;
}

/** AI 助手对话模式（auto 自动路由 / chat 自由对话 / agent 数据查询 / rag 知识库） */
export const AI_CHAT_MODE_OPTIONS: AiOption[] = [
  { value: 'auto', label: '自动' },
  { value: 'chat', label: '对话' },
  { value: 'agent', label: '数据查询' },
  { value: 'rag', label: '知识库' }
];

/** AI 图片生成尺寸选项 */
export const IMAGE_SIZE_OPTIONS: AiOption[] = [
  { label: '1024 × 1024（方形）', value: '1024*1024' },
  { label: '1280 × 1280（方形）', value: '1280*1280' },
  { label: '720 × 1280（竖版）', value: '720*1280' },
  { label: '1280 × 720（横版）', value: '1280*720' }
];
