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

/** 通知分页响应（后端 TableDataInfo：rows/total 在响应体顶层，无 data 包裹） */
export interface NotificationPage {
  code?: number;
  msg?: string;
  rows: NotificationItem[];
  total: number;
}

export function pageNotifications(params: { pageNum?: number; pageSize?: number }): Promise<NotificationPage> {
  return request({
    url: '/im/notification/page',
    method: 'get',
    params
  }) as unknown as Promise<NotificationPage>;
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
