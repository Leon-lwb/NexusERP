import request from '@/utils/request';
import { AxiosPromise } from 'axios';

export function pageNotifications(params: { pageNum?: number; pageSize?: number }) {
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

export function unreadNotificationCount(): AxiosPromise<number> {
  return request({
    url: '/im/notification/unread/count',
    method: 'get'
  });
}
