import { getToken } from '@/utils/auth';
import { ElNotification } from 'element-plus';
import { useNoticeStore } from '@/store/modules/notice';

// 初始化
export const initSSE = (url: any) => {
  if (import.meta.env.VITE_APP_SSE === 'false') {
    return;
  }

  url = url + '?Authorization=Bearer ' + getToken() + '&clientid=' + import.meta.env.VITE_APP_CLIENT_ID;
  const { data, error } = useEventSource(url, [], {
    autoReconnect: {
      retries: 5,
      delay: 5000,
      onFailed() {
        console.log('Failed to connect after 5 retries');
      }
    }
  });

  watch(error, () => {
    console.log('SSE connection error:', error.value);
    error.value = null;
  });

  watch(data, () => {
    if (!data.value) return;
    const raw = data.value;
    if (typeof raw === 'string') {
      try {
        const obj = JSON.parse(raw);
        if (obj && obj.v === 1 && obj.type && obj.payload) {
          if (obj.type === 'USER_NOTIFICATION') {
            const p = obj.payload;
            useNoticeStore().addNotice({
              message: p.title || '通知',
              read: false,
              time: new Date().toLocaleString()
            });
            ElNotification({
              title: p.title || '站内通知',
              message: p.contentPreview || '',
              type: 'info',
              duration: 4500
            });
            data.value = null;
            return;
          }
          if (obj.type === 'IM_MESSAGE') {
            window.dispatchEvent(new CustomEvent('im-realtime', { detail: obj.payload }));
            const c = obj.payload?.content || '';
            ElNotification({
              title: '即时消息',
              message: c.length > 120 ? c.slice(0, 120) + '…' : c,
              type: 'success',
              duration: 3500
            });
            data.value = null;
            return;
          }
        }
        // AI 消息类型处理（message 字段为 JSON，含 type/content）
        // 注意：AI 消息格式与系统通知不同，独立处理
        if (obj.message && typeof obj.message === 'string') {
          try {
            const aiMsg = JSON.parse(obj.message);
            if (aiMsg.type && aiMsg.type.startsWith('AI_')) {
              window.dispatchEvent(new CustomEvent('ai-sse-message', { detail: aiMsg }));
              data.value = null;
              return;
            }
          } catch {
            /* message 不是 JSON，忽略 */
          }
        }
      } catch {
        /* 非 JSON */
      }
    }
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
    data.value = null;
  });
};
