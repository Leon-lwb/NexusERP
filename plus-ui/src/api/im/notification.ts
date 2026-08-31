import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 通知列表项（/im/notification/page 返回实体） */
export interface NotificationItem {
  notificationId: number | string;
  userId?: number;
  category?: string;
  refType?: string;
  refId?: string;
  title?: string;
  content?: string;
  readFlag?: string;
  createTime?: string;
}

export function pageNotifications(params: { pageNum?: number; pageSize?: number }): AxiosPromise<{ rows: NotificationItem[]; total: number }> {
  return request({
    url: '/im/notification/page',
    method: 'get',
    params
  });
}

export function markNotificationRead(notificationId: number | string): AxiosPromise<void> {
  return request({
    url: '/im/notification/read/' + notificationId,
    method: 'put'
  });
}

/** 全部已读 */
export function markAllNotificationRead(): AxiosPromise<void> {
  return request({
    url: '/im/notification/read/all',
    method: 'put'
  });
}

export function unreadNotificationCount(): AxiosPromise<number> {
  return request({
    url: '/im/notification/unread/count',
    method: 'get'
  });
}
