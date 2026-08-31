import request from '@/utils/request';

/** MCP 市场源类型 */
export interface AiMcpMarket {
  id?: number;
  name: string;
  url: string;
  description?: string;
  authConfig?: string;
  status?: string;
  createTime?: string;
}

/** MCP 市场工具类型 */
export interface AiMcpMarketTool {
  id?: number;
  marketId: number;
  toolName: string;
  toolDescription?: string;
  toolVersion?: string;
  toolMetadata?: string;
  isLoaded?: string;
  localToolId?: number;
  createTime?: string;
}

/** 市场刷新结果 */
export interface McpMarketRefreshResult {
  addedCount: number;
  updatedCount: number;
}

// ==================== MCP 市场源 CRUD ====================

/**
 * 分页查询市场源列表
 */
export function listMarket(query?: AiMcpMarket) {
  return request({
    url: '/ai/mcp/market/list',
    method: 'get',
    params: query
  });
}

/**
 * 查询市场源详情
 */
export function getMarket(id: number) {
  return request({
    url: `/ai/mcp/market/${id}`,
    method: 'get'
  });
}

/**
 * 新增市场源
 */
export function addMarket(data: AiMcpMarket) {
  return request({
    url: '/ai/mcp/market',
    method: 'post',
    data
  });
}

/**
 * 修改市场源
 */
export function updateMarket(data: AiMcpMarket) {
  return request({
    url: '/ai/mcp/market',
    method: 'put',
    data
  });
}

/**
 * 删除市场源
 */
export function delMarket(ids: Array<number | string>) {
  return request({
    url: `/ai/mcp/market/${ids}`,
    method: 'delete'
  });
}

// ==================== 市场工具 ====================

/**
 * 查询市场工具列表
 */
export function listMarketTools(marketId: number) {
  return request({
    url: `/ai/mcp/market/${marketId}/tools`,
    method: 'get'
  });
}

/**
 * 刷新市场工具列表（HTTP GET market.url，解析工具列表）
 */
export function refreshMarketTools(marketId: number) {
  return request({
    url: `/ai/mcp/market/${marketId}/refresh`,
    method: 'post'
  });
}

/**
 * 一键加载市场工具到本地 ai_tool（默认 DISABLED，需手动启用）
 */
export function loadToolToLocal(toolId: number) {
  return request({
    url: `/ai/mcp/market/tools/${toolId}/load`,
    method: 'post'
  });
}

/**
 * 批量加载市场工具
 */
export function batchLoadTools(toolIds: number[]) {
  return request({
    url: '/ai/mcp/market/tools/batch-load',
    method: 'post',
    data: toolIds
  });
}
