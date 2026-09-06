import { getToken } from '@/utils/auth';
import { dispatchRealtimeMessage } from '@/utils/realtimeDispatch';

const MAX_RETRIES = 5;
const RECONNECT_DELAY = 5000;

/**
 * 基于 fetch 的 SSE 客户端。
 * <p>EventSource 无法自定义请求头，旧实现把 token 拼在 URL query（易进日志/代理缓存）；
 * 这里用 fetch + ReadableStream 手动解析 SSE 帧，鉴权方式与普通 API 一致（Authorization 请求头）。</p>
 *
 * @param url SSE 端点地址
 * @param onMessage 收到 data 帧时的回调
 * @returns 关闭连接的函数
 */
const connectSSE = (url: string, onMessage: (data: string) => void) => {
  const controller = new AbortController();
  let closed = false;
  let retries = 0;

  const connect = async () => {
    while (!closed) {
      try {
        const res = await fetch(url, {
          method: 'GET',
          headers: {
            Authorization: 'Bearer ' + getToken(),
            clientid: import.meta.env.VITE_APP_CLIENT_ID
          },
          signal: controller.signal
        });
        if (!res.ok || !res.body) {
          throw new Error(`SSE connect failed: ${res.status}`);
        }
        // 连接成功后重置重试计数
        retries = 0;
        const reader = res.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';
        // 解析 SSE 帧：以空行分隔，取 data: 行（多行 data 以 \n 合并，与 EventSource 行为一致）
        for (;;) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          let idx;
          while ((idx = buffer.indexOf('\n\n')) !== -1) {
            const rawEvent = buffer.slice(0, idx);
            buffer = buffer.slice(idx + 2);
            const data = rawEvent
              .split('\n')
              .filter((line) => line.startsWith('data:'))
              .map((line) => line.slice(5).replace(/^ /, ''))
              .join('\n');
            if (data) {
              onMessage(data);
            }
          }
        }
        // 服务端断开：视为连接失败，走重连
        throw new Error('SSE stream closed by server');
      } catch {
        if (closed || controller.signal.aborted) {
          return;
        }
        retries++;
        if (retries > MAX_RETRIES) {
          return;
        }
        await new Promise((resolve) => setTimeout(resolve, RECONNECT_DELAY));
      }
    }
  };
  connect();

  return () => {
    closed = true;
    controller.abort();
  };
};

// 初始化
export const initSSE = (url: any) => {
  if (import.meta.env.VITE_APP_SSE === 'false') {
    return;
  }
  connectSSE(url, (data) => {
    if (!data) return;
    dispatchRealtimeMessage(data);
  });
};
