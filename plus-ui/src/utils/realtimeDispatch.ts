import { ElNotification } from 'element-plus';
import { useNoticeStore } from '@/store/modules/notice';

/** 去除 HTML 标签，用于弹出框显示纯文本 */
const stripHtml = (html: string) => html.replace(/<[^>]*>/g, '');

/**
 * 统一处理实时推送消息（SSE / WebSocket 共用）。
 *
 * 消息协议：
 * - v=1 信封：{ v: 1, type, payload }，type 取值 USER_NOTIFICATION / IM_MESSAGE
 * - AI 消息：顶层 type 以 AI_ 开头，或 message 字段为 JSON 且内含 AI_ type
 * - 安灯消息：type = ANDON，分发到安灯看板
 * - 其余：作为站内通知兜底弹窗
 *
 * @param raw 原始消息字符串
 */
export const dispatchRealtimeMessage = (raw: string) => {
  if (typeof raw !== 'string') return;

  try {
    const obj = JSON.parse(raw);
    if (obj && obj.v === 1 && obj.type && obj.payload) {
      if (obj.type === 'USER_NOTIFICATION') {
        const p = obj.payload;
        const noticeStore = useNoticeStore();
        noticeStore.addNotice({
          id: p.notificationId,
          message: p.title || '通知',
          title: p.title || '通知',
          read: false,
          time: new Date().toLocaleString(),
          contentPreview: p.contentPreview || undefined,
          contentUrl: p.contentUrl || undefined,
          category: p.category,
          refType: p.refType,
          refId: p.refId
        });
        // 未读计数 +1（铃铛角标）
        noticeStore.incrUnread();
        ElNotification({
          title: p.title || '站内通知',
          message: stripHtml(p.contentPreview || ''),
          type: 'info',
          duration: 4500
        });
        return;
      }
      if (obj.type === 'IM_MESSAGE') {
        // 通知与未读由 IM store 统一处理（含活跃会话抑制与标题闪烁）
        window.dispatchEvent(new CustomEvent('im-realtime', { detail: obj.payload }));
        return;
      }
      // P1-6: 已读回执推送，复用 im-realtime 通道，IM store 内部按 type 分流
      if (obj.type === 'IM_MESSAGE_READ') {
        const payload = { ...obj.payload, type: 'IM_MESSAGE_READ' };
        window.dispatchEvent(new CustomEvent('im-realtime', { detail: payload }));
        return;
      }
    }
    // AI 消息类型处理（顶层 type 字段以 AI_ 开头）
    if (obj.type && typeof obj.type === 'string' && obj.type.startsWith('AI_')) {
      window.dispatchEvent(new CustomEvent('ai-sse-message', { detail: obj }));
      return;
    }
    // MES 安灯消息（type=ANDON），分发到安灯看板自行处理
    if (obj.type === 'ANDON') {
      window.dispatchEvent(new CustomEvent('mes-andon', { detail: obj }));
      return;
    }
    // AI 消息类型处理（message 字段为 JSON，含 type/content）
    if (obj.message && typeof obj.message === 'string') {
      try {
        const aiMsg = JSON.parse(obj.message);
        if (aiMsg.type && aiMsg.type.startsWith('AI_')) {
          window.dispatchEvent(new CustomEvent('ai-sse-message', { detail: aiMsg }));
          return;
        }
      } catch {
        /* message 不是 JSON，忽略 */
      }
    }
  } catch {
    /* 非 JSON，走兜底通知 */
  }

  // 兜底：无法识别的消息作为站内通知弹窗
  useNoticeStore().addNotice({
    message: raw,
    read: false,
    time: new Date().toLocaleString()
  });
  ElNotification({
    title: '消息',
    message: raw,
    type: 'success',
    duration: 3000
  });
};
