import { getToken } from '@/utils/auth';
import { dispatchRealtimeMessage } from '@/utils/realtimeDispatch';

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
    },
    onDisconnected() {
    },
    onMessage: (_, e) => {
      if (typeof e.data === 'string' && e.data.trim() === 'ping') {
        return;
      }
      if (typeof e.data === 'string') {
        dispatchRealtimeMessage(e.data);
      }
    }
  });
};
