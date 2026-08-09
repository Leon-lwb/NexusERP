import request from '@/utils/request';

/** AI 流程定义类型 */
export interface AiFlow {
  id: number | string;
  flowName: string;
  flowKey: string;
  description?: string;
  status: 'DRAFT' | 'PUBLISHED' | 'OFFLINE';
  version: number;
  configJson?: string;
  triggerType?: string;
  cronExpr?: string;
  webhookToken?: string;
  maxRetry?: number;
  createTime?: string;
  createBy?: number;
  updateTime?: string;
}

/** AI 流程运行实例类型 */
export interface AiFlowInstance {
  id: number | string;
  flowId: number | string;
  flowName?: string;
  flowVersion: number;
  triggerType: 'MANUAL' | 'SCHEDULE' | 'TEST' | 'WEBHOOK';
  inputJson?: string;
  outputJson?: string;
  /** 启动时配置快照（仅详情接口返回，列表不返回；历史重放用） */
  configSnapshot?: string;
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'STOPPED';
  errorMsg?: string;
  durationMs?: number;
  nodeTotal?: number;
  nodeSuccess?: number;
  nodeFailed?: number;
  startTime?: string;
  endTime?: string;
  createTime?: string;
}

/** AI 流程节点运行记录类型 */
export interface AiFlowNodeRun {
  id: number | string;
  instanceId: number | string;
  nodeId: string;
  nodeName?: string;
  nodeType: string;
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED';
  inputJson?: string;
  outputJson?: string;
  errorMsg?: string;
  retryCount?: number;
  durationMs?: number;
  startTime?: string;
  endTime?: string;
}

/** 节点参数 schema（对齐后端 NodeParamSchema） */
export interface NodeParamSchema {
  name: string;
  label: string;
  type: 'string' | 'number' | 'boolean' | 'options' | 'textarea' | 'json' | 'conditionList';
  defaultValue?: string;
  options?: Array<{ name: string; value: string }>;
  required?: boolean;
  placeholder?: string;
  /** 编辑器增强标记：keyvalue（键值对行编辑）/ toolArgs（工具参数表单） */
  editor?: string;
  /** 候选来源：aiModel（AI 模型下拉）/ aiKnowledge（知识库下拉） */
  optionsSource?: string;
  /** 扩展属性（如 conditionList 的 operators、erp_tool 的 toolArgs） */
  attrs?: Record<string, unknown>;
}

/** 节点类型元数据（/ai/flow/node-types 返回） */
export interface NodeTypeMeta {
  type: string;
  displayName: string;
  params: NodeParamSchema[];
}

/**
 * 分页查询流程列表
 * @param params 查询参数
 */
export function listFlow(params?: Record<string, unknown>) {
  return request({
    url: '/ai/flow/list',
    method: 'get',
    params
  });
}

/**
 * 查询流程详情（含 configJson）
 * @param flowId 流程ID
 */
export function getFlow(flowId: number | string) {
  return request({
    url: `/ai/flow/${flowId}`,
    method: 'get'
  });
}

/**
 * 新建流程（存草稿）
 * @param data 流程数据
 */
export function addFlow(data: Partial<AiFlow>) {
  return request({
    url: '/ai/flow',
    method: 'post',
    data
  });
}

/**
 * 修改流程
 * @param data 流程数据
 */
export function updateFlow(data: Partial<AiFlow>) {
  return request({
    url: '/ai/flow',
    method: 'put',
    data
  });
}

/**
 * 发布流程（校验图合法性后版本+1）
 * @param flowId 流程ID
 */
export function publishFlow(flowId: number | string) {
  return request({
    url: '/ai/flow/publish',
    method: 'post',
    params: { flowId }
  });
}

/**
 * 下线流程
 * @param flowId 流程ID
 */
export function offlineFlow(flowId: number | string) {
  return request({
    url: '/ai/flow/offline',
    method: 'post',
    params: { flowId }
  });
}

/**
 * 删除流程
 * @param flowIds 流程ID数组
 */
export function deleteFlow(flowIds: Array<number | string>) {
  return request({
    url: `/ai/flow/${flowIds.join(',')}`,
    method: 'delete'
  });
}

/**
 * 运行流程（异步，返回实例ID）
 * @param flowId 流程ID
 * @param input 运行入参 JSON 字符串
 */
export function runFlow(flowId: number | string, input?: string) {
  return request({
    url: '/ai/flow/run',
    method: 'post',
    data: { flowId, input }
  });
}

/**
 * 设计器测试运行（对齐 n8n Execute Workflow：无需发布，用画布当前配置）
 * @param flowId 流程ID
 * @param configJson 画布当前配置（可选，null 时回退 DB 配置）
 * @param input 运行入参 JSON 字符串
 */
export function runFlowTest(flowId: number | string, configJson?: string, input?: string) {
  return request({
    url: '/ai/flow/run-test',
    method: 'post',
    data: { flowId, configJson, input }
  });
}

/**
 * 停止运行中的实例
 * @param instanceId 实例ID
 */
export function stopFlowRun(instanceId: number | string) {
  return request({
    url: '/ai/flow/stop',
    method: 'post',
    data: { instanceId }
  });
}

/**
 * 查询节点类型元数据（前端面板 + 参数表单驱动）
 */
export function listNodeTypes() {
  return request({
    url: '/ai/flow/node-types',
    method: 'get'
  });
}

/**
 * 分页查询运行实例列表
 * @param params 查询参数
 */
export function listInstance(params?: Record<string, unknown>) {
  return request({
    url: '/ai/flow/instance/list',
    method: 'get',
    params
  });
}

/**
 * 查询实例详情
 * @param instanceId 实例ID
 */
export function getInstance(instanceId: number | string) {
  return request({
    url: `/ai/flow/instance/${instanceId}`,
    method: 'get'
  });
}

/**
 * 查询实例的节点运行记录
 * @param instanceId 实例ID
 */
export function listNodeRuns(instanceId: number | string) {
  return request({
    url: `/ai/flow/instance/${instanceId}/nodes`,
    method: 'get'
  });
}

/**
 * 执行级续跑（从失败节点重跑）
 * @param instanceId 实例ID
 */
export function retryInstance(instanceId: number | string) {
  return request({
    url: `/ai/flow/instance/${instanceId}/retry`,
    method: 'post'
  });
}

/**
 * 单节点测试（设计器调试）
 * @param flowId 流程ID
 * @param nodeId 节点ID
 * @param mockState mock 状态 JSON 字符串
 */
export function nodeTest(flowId: number | string, nodeId: string, mockState?: string) {
  return request({
    url: '/ai/flow/node-test',
    method: 'post',
    data: { flowId, nodeId, mockState }
  });
}
