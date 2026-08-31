/**
 * AI 模型表单纯函数工具（无 Vue 依赖、无 API 调用，供单测与表单复用）
 * <p>模板表与后端 AiModelCapabilityEnum.defaultInput/defaultOutput 对齐：
 * TEXT→TEXT/TEXT、VISION→TEXT,IMAGE/TEXT、AUDIO→TEXT/TEXT、
 * EMBEDDING→TEXT/VECTOR、RERANK→TEXT/RANKING、IMAGE→TEXT/IMAGE。</p>
 */

/** 能力规范顺序（与后端 AiModelCapabilityEnum 枚举声明顺序一致） */
export const CAPABILITY_ORDER = ['TEXT', 'IMAGE', 'VIDEO', 'AUDIO', 'FILE', 'VECTOR', 'RANKING'] as const;

/** 模型类型能力模板：表单默认值 + 字段显隐/锁定开关 */
export interface ModelTypeTemplate {
  /** 默认输入能力（规范顺序） */
  inputCapabilities: string[];
  /** 默认输出能力（规范顺序） */
  outputCapabilities: string[];
  /** 是否锁定能力勾选（EMBEDDING/RERANK 输入输出由类型决定） */
  lockCapabilities: boolean;
  /** 是否必填向量维度（仅 EMBEDDING） */
  requiresEmbeddingDimension: boolean;
  /** 是否隐藏温度参数（向量/重排模型无采样概念） */
  hideTemperature: boolean;
  /** 是否隐藏最大输出 Token（向量/重排模型不按输出 Token 计） */
  hideMaxTokens: boolean;
}

/** 各模型类型的默认能力模板 + 表单开关（键为规范化的模型类型码） */
const MODEL_TYPE_TEMPLATES: Record<string, ModelTypeTemplate> = {
  TEXT: {
    inputCapabilities: ['TEXT'],
    outputCapabilities: ['TEXT'],
    lockCapabilities: false,
    requiresEmbeddingDimension: false,
    hideTemperature: false,
    hideMaxTokens: false
  },
  VISION: {
    inputCapabilities: ['TEXT', 'IMAGE'],
    outputCapabilities: ['TEXT'],
    lockCapabilities: false,
    requiresEmbeddingDimension: false,
    hideTemperature: false,
    hideMaxTokens: false
  },
  AUDIO: {
    inputCapabilities: ['TEXT'],
    outputCapabilities: ['TEXT'],
    lockCapabilities: false,
    requiresEmbeddingDimension: false,
    hideTemperature: false,
    hideMaxTokens: false
  },
  EMBEDDING: {
    inputCapabilities: ['TEXT'],
    outputCapabilities: ['VECTOR'],
    lockCapabilities: true,
    requiresEmbeddingDimension: true,
    hideTemperature: true,
    hideMaxTokens: true
  },
  RERANK: {
    inputCapabilities: ['TEXT'],
    outputCapabilities: ['RANKING'],
    lockCapabilities: true,
    requiresEmbeddingDimension: false,
    hideTemperature: true,
    hideMaxTokens: true
  },
  IMAGE: {
    inputCapabilities: ['TEXT'],
    outputCapabilities: ['IMAGE'],
    lockCapabilities: false,
    requiresEmbeddingDimension: false,
    hideTemperature: false,
    hideMaxTokens: false
  }
};

/**
 * 应用模型类型的默认能力模板
 * <p>大小写/空白宽容（如 ' embedding ' 等价 'EMBEDDING'）；
 * 未知或空白类型抛错（与后端 AiModelCapabilityEnum.template 行为一致，暴露脏数据而非静默兜底）。
 * 返回深拷贝，调用方可安全修改。</p>
 *
 * @param modelType 模型类型（字典 ai_model_type：TEXT/VISION/AUDIO/EMBEDDING/RERANK/IMAGE）
 * @throws Error 模型类型为空或未知
 */
export function applyModelTypeTemplate(modelType: string): ModelTypeTemplate {
  if (modelType === null || modelType === undefined || modelType.trim() === '') {
    throw new Error('模型类型不能为空');
  }
  const key = modelType.trim().toUpperCase();
  const template = MODEL_TYPE_TEMPLATES[key];
  if (!template) {
    throw new Error(`未知模型类型: ${modelType}`);
  }
  return {
    ...template,
    inputCapabilities: [...template.inputCapabilities],
    outputCapabilities: [...template.outputCapabilities]
  };
}

/**
 * 规范化能力值列表：去空白/统一大写、去重、丢弃未知值，按规范顺序排序
 * <p>未知值丢弃而非抛错——用于渲染存量数据，脏值不应让表单崩溃（保存时后端会校验）。</p>
 *
 * @param values 原始能力列表（允许 null/undefined/空数组）
 * @returns 规范顺序的能力数组（空输入返回 []）
 */
export function normalizeCapabilityValues(values: Array<string> | null | undefined): string[] {
  if (!values || !values.length) {
    return [];
  }
  const normalized = new Set<string>();
  for (const value of values) {
    const key = (value ?? '').trim().toUpperCase();
    if ((CAPABILITY_ORDER as readonly string[]).includes(key)) {
      normalized.add(key);
    }
  }
  return (CAPABILITY_ORDER as readonly string[]).filter((capability) => normalized.has(capability));
}

/**
 * 拆分逗号分隔的能力串（ai_model.input_capabilities / output_capabilities 存储格式）
 * <p>仅做机械拆分：按逗号切割、去首尾空白、统一大写、丢弃空段；
 * 去重与排序见 {@link normalizeCapabilityValues}。</p>
 *
 * @param csv 逗号分隔能力串（null/空白返回 []）
 */
export function splitCapabilities(csv: string | null | undefined): string[] {
  if (csv === null || csv === undefined || csv.trim() === '') {
    return [];
  }
  return csv
    .split(',')
    .map((value) => value.trim().toUpperCase())
    .filter((value) => value !== '');
}

/**
 * 拼接能力数组为逗号分隔串（入库格式）
 *
 * @param values 能力数组（null/undefined 返回 ''，空白项跳过）
 */
export function joinCapabilities(values: Array<string> | null | undefined): string {
  if (values === null || values === undefined) {
    return '';
  }
  return values
    .map((value) => (value ?? '').trim())
    .filter((value) => value !== '')
    .join(',');
}
