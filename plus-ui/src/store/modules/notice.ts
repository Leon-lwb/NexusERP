import { defineStore } from 'pinia';
import { reactive } from 'vue';

interface NoticeItem {
  /** 通知 ID（持久化与实时推送共有，去重键） */
  id?: number | string;
  title?: string;
  read: boolean;
  message: any;
  time: string;
  /** 正文摘要（去 HTML） */
  contentPreview?: string;
  contentUrl?: string;
  category?: string;
  refType?: string;
  refId?: string;
}

export const useNoticeStore = defineStore('notice', () => {
  const state = reactive({
    notices: [] as NoticeItem[],
    /** 未读计数（后端 /unread/count 初始化 + 实时推送 +1 + 已读 -1） */
    unreadCount: 0
  });

  const addNotice = (notice: NoticeItem) => {
    // 按 id 去重（同一通知重复推送不叠加）
    if (notice.id !== undefined && notice.id !== null) {
      const exists = state.notices.find((n) => n.id === notice.id);
      if (exists) {
        exists.read = notice.read;
        return;
      }
    } else if (notice.message !== undefined) {
      // 无 id 消息（如登录欢迎等 SSE 兜底）按内容去重，避免多次登录/重复推送叠加
      const exists = state.notices.find((n) => n.id === undefined && n.message === notice.message);
      if (exists) {
        exists.read = notice.read;
        // 刷新到达时间（如 SPA 内重新登录的欢迎语），避免显示上一次登录的旧时间
        exists.time = notice.time;
        return;
      }
    }
    state.notices.push(notice);
  };

  const removeNotice = (notice: NoticeItem) => {
    state.notices.splice(state.notices.indexOf(notice), 1);
  };

  /** 标记单条已读（本地） */
  const markRead = (id: number | string) => {
    const item = state.notices.find((n) => n.id === id);
    if (item && !item.read) {
      item.read = true;
      if (state.unreadCount > 0) state.unreadCount--;
    }
  };

  //实现全部已读
  const readAll = () => {
    state.notices.forEach((item: any) => {
      item.read = true;
    });
    state.unreadCount = 0;
  };

  const clearNotice = () => {
    state.notices = [];
  };

  /** 设置未读计数（登录/刷新后初始化） */
  const setUnreadCount = (n: number) => {
    state.unreadCount = n;
  };

  /** 实时推送到达：未读 +1 */
  const incrUnread = () => {
    state.unreadCount++;
  };

  return {
    state,
    addNotice,
    removeNotice,
    markRead,
    readAll,
    clearNotice,
    setUnreadCount,
    incrUnread
  };
});
