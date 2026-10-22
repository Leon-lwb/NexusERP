import request from '@/utils/request';

/** AI 会话类型 */
export interface AiSession {
  id: number | string;
  userId: number;
  title: string;
  modelName: string;
  createTime: string;
}

/** AI 消息类型 */
export interface AiMessage {
  id: number | string;
  sessionId: number | string;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  /** 推理过程内容（enableThinking 时存） */
  thinkingContent?: string;
  createTime: string;
}

/**
 * 发送 AI 消息
 * @param data 聊天请求数据
 * @returns 会话ID
 */
export function sendAiMessage(data: {
  sessionId?: number | string;
  /** PC 在发送前生成，仅用于本次 SSE 关联，不是业务幂等键。 */
  clientRequestId?: string;
  content: string;
  model?: string;
  mode?: string;
  /** 知识库模式的明确范围，雪花ID按字符串无损透传。 */
  knowledgeId?: string;
  /** 是否开启推理（Thinking）模式 */
  enableThinking?: boolean;
  /** 是否开启联网搜索 */
  enableWebSearch?: boolean;
  /** 本次对话启用的 MCP 工具 ID 列表 */
  enabledToolIds?: number[];
  /** 用户主动选择的工程设备标识，雪花 ID 按字符串传输。 */
  engineeringDeviceId?: string;
  /** 同一条工程请求的 UUID，网络重试复用以避免重复改图。 */
  engineeringRequestId?: string;
}) {
  return request({
    url: '/ai/chat/send',
    method: 'post',
    data
  });
}

/**
 * 查询聊天历史
 * @param sessionId 会话ID
 */
export function getChatHistory(sessionId: number | string) {
  return request({
    url: `/ai/chat/history/${sessionId}`,
    method: 'get'
  });
}

/**
 * 查询当前用户的会话列表
 */
export function getChatSessions() {
  return request({
    url: '/ai/chat/sessions',
    method: 'get'
  });
}

/**
 * 删除会话（级联删除消息）
 * @param ids 会话ID数组
 */
export function deleteSession(ids: Array<number | string>) {
  return request({
    url: `/ai/chat/session/${ids}`,
    method: 'delete'
  });
}

/**
 * 重命名会话
 * @param data 会话信息（含 id 和 title）
 */
export function renameSession(data: { id: number | string; title: string }) {
  return request({
    url: '/ai/chat/session',
    method: 'put',
    data
  });
}

/**
 * 消息反馈（点赞/点踩）
 * @param messageId 消息ID
 * @param feedback 反馈类型：LIKE / DISLIKE / NONE
 */
export function messageFeedback(messageId: number | string, feedback: string) {
  return request({
    url: `/ai/chat/message/${messageId}/feedback`,
    method: 'put',
    params: { feedback }
  });
}

/**
 * 导出会话聊天记录为 Markdown 文件
 * @param sessionId 会话ID
 */
export function exportSession(sessionId: number | string): Promise<Blob> {
  return request({
    url: `/ai/chat/export/${sessionId}`,
    method: 'get',
    responseType: 'blob'
  });
}

/**
 * 查询当前会话的 Token 用量
 * @param sessionId 会话ID
 */
export function getTokenUsage(sessionId: number | string) {
  return request({
    url: `/ai/chat/token-usage/${sessionId}`,
    method: 'get'
  });
}
