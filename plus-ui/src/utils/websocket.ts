import { getToken } from '@/utils/auth';
import { ElNotification } from 'element-plus';
import { useNoticeStore } from '@/store/modules/notice';

// 初始化socket
export const initWebSocket = (url: any) => {
  if (import.meta.env.VITE_APP_WEBSOCKET === 'false') {
    return;
  }
  url = url + '?Authorization=Bearer ' + getToken() + '&clientid=' + import.meta.env.VITE_APP_CLIENT_ID;
  useWebSocket(url, {
    autoReconnect: {
      // 重连最大次数
      retries: 3,
      // 重连间隔
      delay: 1000,
      onFailed() {
        console.log('websocket重连失败');
      }
    },
    heartbeat: {
      message: JSON.stringify({ type: 'ping' }),
      // 发送心跳的间隔
      interval: 10000,
      // 接收到心跳response的超时时间
      pongTimeout: 2000
    },
    onConnected() {
      console.log('websocket已经连接');
    },
    onDisconnected() {
      console.log('websocket已经断开');
    },
    onMessage: (_, e) => {
      if (typeof e.data === 'string' && e.data.trim() === 'ping') {
        return;
      }
      if (typeof e.data === 'string') {
        try {
          const obj = JSON.parse(e.data);
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
              return;
            }
            if (obj.type === 'IM_MESSAGE') {
              // 通知与未读由 IM store 统一处理（含活跃会话抑制与标题闪烁）
              window.dispatchEvent(new CustomEvent('im-realtime', { detail: obj.payload }));
              return;
            }
          }
        } catch {
          /* 非 JSON 走旧逻辑 */
        }
      }
      useNoticeStore().addNotice({
        message: e.data,
        read: false,
        time: new Date().toLocaleString()
      });
      ElNotification({
        title: '消息',
        message: e.data,
        type: 'success',
        duration: 3000
      });
    }
  });
};
