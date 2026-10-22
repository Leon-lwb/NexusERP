/** 普通聊天事件的关联信息；Flow 与工程事件沿用各自的契约。 */
export interface AiChatStreamEvent {
  type: string;
  content?: string;
  clientRequestId: string;
  requestId: string;
  sessionId: string;
  sequence: number;
  messageId?: string;
}

const chatTypes = new Set([
  'AI_THINKING',
  'AI_CONTENT_INCREMENT',
  'AI_CONTENT',
  'AI_DONE',
  'AI_ERROR',
  'AI_CANCEL_REQUESTED',
  'AI_CANCELLED',
  'AI_CHART',
  'AI_IMAGE',
  'AI_WEB_SEARCH',
  'AI_THINKING_TOKEN',
  'AI_TOOL_START',
  'AI_TOOL_END',
  'AI_AGENT_THINKING'
]);
const uuidPattern = /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;

/** HTTP 兼容安全整数，所有雪花 ID 保持为正十进制 long 字符串。 */
export function normalizeAiChatId(value: unknown): string | null {
  if (typeof value === 'number') {
    if (!Number.isSafeInteger(value) || value <= 0) return null;
    return String(value);
  }
  if (typeof value !== 'string' || !/^[1-9]\d{0,18}$/.test(value)) return null;
  if (value.length === 19 && value > '9223372036854775807') return null;
  return value;
}

/** getRandomValues 在非 HTTPS 内网也可用，避免依赖仅安全上下文支持的 randomUUID。 */
export function createAiClientRequestId(): string {
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/** 每个组件独立持有一次发送的接收状态，不读取 Vue、全局 SSE 或 HTTP 状态。 */
export function createAiChatStream() {
  let active: {
    clientRequestId: string;
    requestId: string | null;
    sessionId: string | null;
    sequence: number;
    terminal: boolean;
    cancelling: boolean;
  } | null = null;

  return {
    /** 必须在 POST 之前调用，允许新会话由首个匹配的 SSE 先绑定。 */
    begin(sessionId: unknown = null): string {
      const normalized = sessionId == null ? null : normalizeAiChatId(sessionId);
      if (sessionId != null && !normalized) throw new Error('无效的 AI 会话 ID');
      const nonce = createAiClientRequestId();
      active = { clientRequestId: nonce, requestId: null, sessionId: normalized, sequence: 0, terminal: false, cancelling: false };
      return nonce;
    },

    /** 校验全部标识后才绑定及推进序号，拒绝的事件不会改变状态。 */
    accept(event: unknown): event is AiChatStreamEvent {
      if (!active || active.terminal || !event || typeof event !== 'object') return false;
      const detail = event as Partial<AiChatStreamEvent>;
      if (!chatTypes.has(detail.type) || detail.clientRequestId !== active.clientRequestId) return false;
      if (active.cancelling && !['AI_CANCEL_REQUESTED', 'AI_CANCELLED', 'AI_DONE', 'AI_ERROR'].includes(detail.type)) return false;
      if (typeof detail.requestId !== 'string' || !uuidPattern.test(detail.requestId)) return false;
      if (typeof detail.sessionId !== 'string' || !normalizeAiChatId(detail.sessionId)) return false;
      if (detail.messageId !== undefined && (typeof detail.messageId !== 'string' || !normalizeAiChatId(detail.messageId))) return false;
      if (!Number.isSafeInteger(detail.sequence) || detail.sequence <= active.sequence) return false;
      // 正文/过程载荷必须为字符串；无正文的思考状态及完成事件仍可省略 content。
      const contentOptional =
        detail.type === 'AI_THINKING' || detail.type === 'AI_DONE' || detail.type === 'AI_CANCEL_REQUESTED' || detail.type === 'AI_CANCELLED';
      if (typeof detail.content !== 'string' && !(contentOptional && detail.content == null)) return false;
      if (active.requestId && active.requestId !== detail.requestId) return false;
      if (active.sessionId && active.sessionId !== detail.sessionId) return false;
      active.requestId = detail.requestId;
      active.sessionId = detail.sessionId;
      active.sequence = detail.sequence;
      if (detail.type === 'AI_CANCEL_REQUESTED') active.cancelling = true;
      active.terminal = detail.type === 'AI_DONE' || detail.type === 'AI_ERROR' || detail.type === 'AI_CANCELLED';
      return true;
    },

    /** ACK 可晚于终态到达，但不能覆盖已由 SSE 绑定的会话。 */
    ack(nonce: string, sessionId: unknown): boolean {
      if (!active || active.clientRequestId !== nonce) return false;
      const normalized = normalizeAiChatId(sessionId);
      if (!normalized || (active.sessionId && active.sessionId !== normalized)) return false;
      active.sessionId = normalized;
      return true;
    },

    isCurrent(nonce: string): boolean {
      return active?.clientRequestId === nonce;
    },

    get sessionId(): string | null {
      return active?.sessionId ?? null;
    },

    reset() {
      active = null;
    }
  };
}
