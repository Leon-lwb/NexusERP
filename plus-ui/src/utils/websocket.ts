import { getToken } from '@/utils/auth';
import { dispatchRealtimeMessage } from '@/utils/realtimeDispatch';

/**
 * P4-6 W2: WebSocket 连接状态。
 * <p>模块级变量，在 onConnected/onDisconnected 中更新，供 isWsConnected() 查询。
 * <p>同时 dispatch 'im-ws-connected' 事件供 IM store 监听重连后同步离线消息。
 */
let wsConnected = false;

/** 查询 WebSocket 当前是否处于连接状态（用于 IM store 30s 兜底轮询判定） */
export function isWsConnected(): boolean {
  return wsConnected;
}

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
      // P4-6 W2: 标记连接成功 + 通知 IM store 同步离线消息（重连后触发）
      wsConnected = true;
      try {
        window.dispatchEvent(new CustomEvent('im-ws-connected'));
      } catch {
        /* SSR or non-window env: ignore */
      }
    },
    onDisconnected() {
      wsConnected = false;
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
