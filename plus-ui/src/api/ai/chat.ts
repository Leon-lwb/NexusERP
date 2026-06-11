import request from '@/utils/request'

/** AI 会话类型 */
export interface AiSession {
  id: number | string
  userId: number
  title: string
  modelName: string
  createTime: string
}

/** AI 消息类型 */
export interface AiMessage {
  id: number | string
  sessionId: number | string
  role: 'USER' | 'ASSISTANT' | 'SYSTEM'
  content: string
  createTime: string
}

/**
 * 发送 AI 消息
 * @param data 聊天请求数据
 * @returns 会话ID
 */
export function sendAiMessage(data: { sessionId?: number; content: string; model?: string; mode?: string }) {
  return request({
    url: '/ai/chat/send',
    method: 'post',
    data
  })
}

/**
 * 查询聊天历史
 * @param sessionId 会话ID
 */
export function getChatHistory(sessionId: number | string) {
  return request({
    url: `/ai/chat/history/${sessionId}`,
    method: 'get'
  })
}

/**
 * 查询当前用户的会话列表
 */
export function getChatSessions() {
  return request({
    url: '/ai/chat/sessions',
    method: 'get'
  })
}

/**
 * 删除会话（级联删除消息）
 * @param ids 会话ID数组
 */
export function deleteSession(ids: Array<number | string>) {
  return request({
    url: `/ai/chat/session/${ids}`,
    method: 'delete'
  })
}

/**
 * 重命名会话
 * @param data 会话信息（含 id 和 title）
 */
export function renameSession(data: { id: number; title: string }) {
  return request({
    url: '/ai/chat/session',
    method: 'put',
    data
  })
}
