import request from '@/utils/request'

/** AI 工具配置类型 */
export interface AiTool {
  id?: number
  tenantId?: number
  toolName: string
  displayName: string
  description: string
  toolType: string
  configJson?: string
  status: string
}

// ==================== MCP 工具管理 API ====================

/**
 * 查询所有工具列表
 */
export function listTool(query?: object) {
  return request({
    url: '/ai/mcp/tool/list',
    method: 'get',
    params: query
  })
}

/**
 * 查询所有启用的工具
 */
export function listEnabledTool() {
  return request({
    url: '/ai/mcp/tool/enabled',
    method: 'get'
  })
}

/**
 * 根据 ID 查询工具详情
 * @param id 工具ID
 */
export function getTool(id: number) {
  return request({
    url: `/ai/mcp/tool/${id}`,
    method: 'get'
  })
}

/**
 * 新增工具
 * @param data 工具配置数据
 */
export function addTool(data: AiTool) {
  return request({
    url: '/ai/mcp/tool',
    method: 'post',
    data
  })
}

/**
 * 修改工具
 * @param data 工具配置数据
 */
export function updateTool(data: AiTool) {
  return request({
    url: '/ai/mcp/tool',
    method: 'put',
    data
  })
}

/**
 * 批量删除工具
 * @param ids 工具ID数组
 */
export function delTool(ids: Array<number | string>) {
  return request({
    url: `/ai/mcp/tool/${ids}`,
    method: 'delete'
  })
}

/**
 * 启用/停用工具
 * @param id 工具ID
 * @param status 状态（ENABLED/DISABLED）
 */
export function toggleToolStatus(id: number, status: string) {
  return request({
    url: `/ai/mcp/tool/${id}/status`,
    method: 'put',
    params: { status }
  })
}

/**
 * 同步内置工具到数据库
 */
export function syncBuiltinTools() {
  return request({
    url: '/ai/mcp/tool/sync',
    method: 'post'
  })
}

/**
 * 测试工具连接
 * @param id 工具ID
 */
export function testTool(id: number) {
  return request({
    url: `/ai/mcp/tool/${id}/test`,
    method: 'post'
  })
}
