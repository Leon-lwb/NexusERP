import request from '@/utils/request';

/** 获取可用的 MCP 工具列表（供对话页面工具选择器使用） */
export function getAvailableMcpTools() {
  return request({
    url: '/ai/mcp/tool/available',
    method: 'get'
  });
}
