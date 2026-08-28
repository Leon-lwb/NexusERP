import request from '@/utils/request';

/** AI 厂商配置类型 */
export interface AiProvider {
  id?: number;
  name: string;
  apiHost: string;
  apiKey?: string;
  providerType: string;
  status: string;
}

/** AI 模型配置类型 */
export interface AiModel {
  id?: number;
  providerId: number;
  providerName?: string;
  /** 厂商类型（OPENAI/OLLAMA/DEEPSEEK/DASHSCOPE/ZHIPU...），区分本地 vs 官网 */
  providerType?: string;
  /** API 请求地址，区分实际调用 endpoint */
  apiHost?: string;
  modelName: string;
  displayName?: string;
  modelType: string;
  /** 上下文窗口大小（Token 数） */
  contextWindow?: number;
  /** 输入能力（逗号分隔，字典 ai_model_capability，如 TEXT,IMAGE） */
  inputCapabilities?: string;
  /** 输出能力（逗号分隔，字典 ai_model_capability，如 TEXT,VECTOR） */
  outputCapabilities?: string;
  /** API 协议（字典 ai_model_api_protocol，空由厂商类型推断默认协议） */
  apiProtocol?: string;
  /** 向量维度（仅 EMBEDDING 模型，用于与实测维度比对） */
  embeddingDimension?: number;
  maxTokens?: number;
  temperature?: number;
  /** 输入价格（元/百万Token） */
  inputPricePerMillion?: number;
  /** 输出价格（元/百万Token） */
  outputPricePerMillion?: number;
  /** 币种（CNY/USD） */
  currency?: string;
  isDefault: string;
  status: string;
}

/** 模型草稿连接测试请求（不落库；不含 API Key，密钥由后端从厂商行内解密使用） */
export interface AiModelTestRequest extends Omit<AiModel, 'id' | 'status' | 'isDefault'> {
  providerId: number;
  /** 图片生成测试二次确认（计费测试需显式传 true） */
  confirmBillableImageTest?: boolean;
}

/** 模型连接测试结果（失败信息已脱敏） */
export interface AiModelTestResult {
  success: boolean;
  message: string;
  latencyMs?: number;
  providerType?: string;
  apiProtocol?: string;
  /** 实测向量维度（仅 EMBEDDING 测试返回，用于与配置值比对） */
  detectedEmbeddingDimension?: number;
}

/** 厂商调用协议策略（供模型表单的协议下拉约束） */
export interface AiModelPolicy {
  defaultProtocol: string;
  allowedProtocols: string[];
}

// ==================== 厂商管理 API ====================

/**
 * 分页查询AI厂商配置列表
 * @param query 查询参数
 */
export function listProvider(query?: object) {
  return request({
    url: '/ai/provider/list',
    method: 'get',
    params: query
  });
}

/**
 * 根据ID查询AI厂商配置
 * @param id 厂商ID
 */
export function getProvider(id: number) {
  return request({
    url: `/ai/provider/${id}`,
    method: 'get'
  });
}

/**
 * 新增AI厂商配置
 * @param data 厂商配置数据
 */
export function addProvider(data: AiProvider) {
  return request({
    url: '/ai/provider',
    method: 'post',
    data
  });
}

/**
 * 修改AI厂商配置
 * @param data 厂商配置数据
 */
export function updateProvider(data: AiProvider) {
  return request({
    url: '/ai/provider',
    method: 'put',
    data
  });
}

/**
 * 批量删除AI厂商配置
 * @param ids 厂商ID数组
 */
export function delProvider(ids: Array<number | string>) {
  return request({
    url: `/ai/provider/${ids}`,
    method: 'delete'
  });
}

// ==================== 模型管理 API ====================

/**
 * 分页查询AI模型配置列表
 * @param query 查询参数
 */
export function listModel(query?: object) {
  return request({
    url: '/ai/model/list',
    method: 'get',
    params: query
  });
}

/**
 * 查询启用模型选项（按分类，供对话/文生图选择）
 * <p>仅返回 status=0（启用）的模型，不需管理权限</p>
 * @param modelType 模型类型（TEXT/VISION/AUDIO/EMBEDDING/RERANK/IMAGE，支持逗号分隔多类型如 TEXT,VISION）
 * @param requiredInputCapability 要求输入能力包含该值（字典 ai_model_capability，如 TEXT）
 * @param requiredOutputCapability 要求输出能力包含该值（字典 ai_model_capability，如 VECTOR）
 */
export function listModelOptions(modelType?: string, requiredInputCapability?: string, requiredOutputCapability?: string) {
  const params: Record<string, string> = {};
  if (modelType) {
    params.modelType = modelType;
  }
  if (requiredInputCapability) {
    params.requiredInputCapability = requiredInputCapability;
  }
  if (requiredOutputCapability) {
    params.requiredOutputCapability = requiredOutputCapability;
  }
  return request({
    url: '/ai/model/options',
    method: 'get',
    params
  });
}

/**
 * 根据ID查询AI模型配置
 * @param id 模型ID
 */
export function getModel(id: number) {
  return request({
    url: `/ai/model/${id}`,
    method: 'get'
  });
}

/**
 * 新增AI模型配置
 * @param data 模型配置数据
 */
export function addModel(data: AiModel) {
  return request({
    url: '/ai/model',
    method: 'post',
    data
  });
}

/**
 * 修改AI模型配置
 * @param data 模型配置数据
 */
export function updateModel(data: AiModel) {
  return request({
    url: '/ai/model',
    method: 'put',
    data
  });
}

/**
 * 批量删除AI模型配置
 * @param ids 模型ID数组
 */
export function delModel(ids: Array<number | string>) {
  return request({
    url: `/ai/model/${ids}`,
    method: 'delete'
  });
}

/**
 * 设置默认模型
 * @param id 模型ID
 */
export function setDefaultModel(id: number) {
  return request({
    url: `/ai/model/setDefault/${id}`,
    method: 'put'
  });
}

/**
 * 查询厂商的调用协议策略（默认协议 + 允许协议集合，供模型表单下拉约束）
 * <p>权限标识：ai:model:query</p>
 * @param providerId 厂商ID
 */
export function getModelPolicy(providerId: number) {
  return request({
    url: `/ai/model/policy/${providerId}`,
    method: 'get'
  });
}

/**
 * 模型草稿连接测试（不落库、不自动保存模型）
 * <p>权限标识：ai:model:test。图片生成测试需 confirmBillableImageTest=true 二次确认（实际计费）。</p>
 * @param data 测试请求（厂商ID + 模型草稿参数，不含 API Key）
 */
export function testModel(data: AiModelTestRequest) {
  return request({
    url: '/ai/model/test',
    method: 'post',
    data
  });
}
