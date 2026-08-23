/**
 * AI 模块页面本地枚举常量
 * <p>
 * 后端未提供对应的 sys_dict 字典数据，故在前端统一定义，
 * 含中文 label、value 与 el-tag 类型，供查询表单 / 表格标签 / 编辑表单共用。
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

/** 通用状态：正常/停用（value: '1'/'0'）— 模型、厂商、Prompt 模板 */
export const AI_STATUS_OPTIONS: AiOption[] = [
  { label: '正常', value: '1', elTagType: 'success' },
  { label: '停用', value: '0', elTagType: 'danger' }
];

/** 通用启停状态：启用/停用（value: '1'/'0'）— MCP 市场源 */
export const AI_ENABLE_STATUS_OPTIONS: AiOption[] = [
  { label: '启用', value: '1', elTagType: 'success' },
  { label: '停用', value: '0', elTagType: 'danger' }
];

/** 通用是否：是/否（value: '1'/'0'）— 是否默认、市场工具是否已加载 */
export const AI_YES_NO_OPTIONS: AiOption[] = [
  { label: '是', value: '1', elTagType: 'success' },
  { label: '否', value: '0', elTagType: 'info' }
];

/** AI 模型类型（CHAT/EMBEDDING/RERANK/IMAGE） */
export const AI_MODEL_TYPE_OPTIONS: AiOption[] = [
  { label: 'CHAT（对话）', value: 'CHAT', elTagType: 'primary', tagLabel: '对话' },
  { label: 'EMBEDDING（向量）', value: 'EMBEDDING', elTagType: 'success', tagLabel: '向量' },
  { label: 'RERANK（重排）', value: 'RERANK', elTagType: 'warning', tagLabel: '重排' },
  { label: 'IMAGE（文生图）', value: 'IMAGE', elTagType: 'danger', tagLabel: '文生图' }
];

/** AI 厂商类型（OpenAI 兼容体系） */
export const AI_PROVIDER_TYPE_OPTIONS: AiOption[] = [
  { label: 'OpenAI', value: 'OPENAI', elTagType: 'primary' },
  { label: '通义千问', value: 'DASHSCOPE', elTagType: 'success' },
  { label: '智谱AI', value: 'ZHIPU', elTagType: 'warning' },
  { label: 'Ollama', value: 'OLLAMA', elTagType: 'info' }
];

/** 模型计价币种 */
export const AI_CURRENCY_OPTIONS: AiOption[] = [
  { label: '人民币（CNY）', value: 'CNY' },
  { label: '美元（USD）', value: 'USD' }
];

/** MCP 工具类型（表单可选项） */
export const MCP_TOOL_TYPE_OPTIONS: AiOption[] = [
  { label: 'BUILTIN（内置工具）', value: 'BUILTIN', elTagType: 'primary' },
  { label: 'LOCAL（本地 STDIO 工具）', value: 'LOCAL', elTagType: 'info' },
  { label: 'REMOTE（远程 HTTP/SSE 工具）', value: 'REMOTE', elTagType: 'warning' }
];

/** MCP 工具类型标签着色（含历史数据中可能出现的 MCP 类型，仅供表格标签使用） */
export const MCP_TOOL_TYPE_TAG_OPTIONS: AiOption[] = [...MCP_TOOL_TYPE_OPTIONS, { label: 'MCP', value: 'MCP', elTagType: 'success' }];

/** MCP 工具状态（ENABLED/DISABLED） */
export const MCP_TOOL_STATUS_OPTIONS: AiOption[] = [
  { label: '启用', value: 'ENABLED', elTagType: 'success' },
  { label: '停用', value: 'DISABLED', elTagType: 'danger' }
];

/** SQL 示例分类（对应 ERP 数据域） */
export const SQL_EXAMPLE_CATEGORY_OPTIONS: AiOption[] = [
  { label: '库存', value: 'INVENTORY', elTagType: 'warning' },
  { label: '销售', value: 'SALES', elTagType: 'success' },
  { label: '采购', value: 'PURCHASE', elTagType: 'primary' },
  { label: '生产', value: 'PRODUCTION', elTagType: 'danger' }
];

/** SQL 示例状态（注意：'0' 启用 / '1' 禁用，与通用状态取值相反） */
export const SQL_EXAMPLE_STATUS_OPTIONS: AiOption[] = [
  { label: '启用', value: '0', elTagType: 'success' },
  { label: '禁用', value: '1', elTagType: 'info' }
];

/** A/B 测试状态 */
export const AB_TEST_STATUS_OPTIONS: AiOption[] = [
  { label: '待执行', value: 'PENDING', elTagType: 'info' },
  { label: '执行中', value: 'RUNNING', elTagType: 'warning' },
  { label: '已完成', value: 'COMPLETED', elTagType: 'success' }
];

/** 按值查找枚举选项 */
export function getAiOption(options: AiOption[], value?: string | null): AiOption | undefined {
  return options.find((item) => item.value === value);
}

/** 按值取 el-tag 类型（未匹配时返回 'info'） */
export function getAiOptionTagType(options: AiOption[], value?: string | null): ElTagType {
  return getAiOption(options, value)?.elTagType ?? 'info';
}

/** 按值取表格标签文案（优先 tagLabel，其次 label，未匹配时回退原始值） */
export function getAiOptionTagLabel(options: AiOption[], value?: string | null): string {
  if (value == null || value === '') return '';
  const option = getAiOption(options, value);
  return option?.tagLabel ?? option?.label ?? value;
}

/** 知识库文档解析状态（0 待解析 / 1 解析中 / 2 已完成 / 3 失败） */
export const KNOWLEDGE_DOC_STATUS_OPTIONS: AiOption[] = [
  { label: '待解析', value: '0', elTagType: 'info' },
  { label: '解析中', value: '1', elTagType: 'warning' },
  { label: '已完成', value: '2', elTagType: 'success' },
  { label: '失败', value: '3', elTagType: 'danger' }
];

/** 知识图谱构建状态（0 未构建 / 1 构建中 / 2 已完成 / 3 失败） */
export const KG_GRAPH_STATUS_OPTIONS: AiOption[] = [
  { label: '未构建', value: '0', elTagType: 'info' },
  { label: '构建中', value: '1', elTagType: 'warning' },
  { label: '已完成', value: '2', elTagType: 'success' },
  { label: '失败', value: '3', elTagType: 'danger' }
];

/** 知识图谱社区摘要状态（0 待生成 / 1 生成中 / 2 已完成 / 3 失败） */
export const KG_SUMMARY_STATUS_OPTIONS: AiOption[] = [
  { label: '待生成', value: '0', elTagType: 'info' },
  { label: '生成中', value: '1', elTagType: 'warning' },
  { label: '已完成', value: '2', elTagType: 'success' },
  { label: '失败', value: '3', elTagType: 'danger' }
];

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

/** 知识库向量存储类型 */
export const KNOWLEDGE_VECTOR_STORE_OPTIONS: AiOption[] = [
  { label: 'Milvus', value: 'milvus' },
  { label: 'InMemory', value: 'inmemory' }
];
