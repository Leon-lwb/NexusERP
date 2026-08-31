import { getToken } from '@/utils/auth';
import { dispatchRealtimeMessage } from '@/utils/realtimeDispatch';

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
      onFailed() {}
    }
  });

  watch(error, () => {
    error.value = null;
  });

  watch(data, () => {
    if (!data.value) return;
    dispatchRealtimeMessage(data.value);
    data.value = null;
  });
};
