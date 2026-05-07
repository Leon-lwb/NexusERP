import request from '@/utils/request';
import { AxiosPromise } from 'axios';

export function listConversations(): AxiosPromise<any[]> {
  return request({
    url: '/im/chat/conversations',
    method: 'get'
  });
}

export function sendPrivateMessage(data: { toUserId: number; content: string; msgType?: string }) {
  return request({
    url: '/im/chat/private/send',
    method: 'post',
    data
  });
}

export function sendGroupMessage(data: { groupId: string | number; content: string; msgType?: string }) {
  return request({
    url: '/im/chat/group/send',
    method: 'post',
    data
  });
}

export function privateHistory(params: { peerUserId: number; pageNum?: number; pageSize?: number }) {
  return request({
    url: '/im/chat/private/history',
    method: 'get',
    params
  });
}

export function groupHistory(params: { groupId: string | number; pageNum?: number; pageSize?: number }) {
  return request({
    url: '/im/chat/group/history',
    method: 'get',
    params
  });
}

export function createGroup(data: { groupName: string; avatar?: string; memberUserIds: number[] }) {
  return request({
    url: '/im/chat/group',
    method: 'post',
    data
  });
}

export function addGroupMembers(groupId: string | number, data: { memberUserIds: number[] }) {
  return request({
    url: '/im/chat/group/' + groupId + '/members',
    method: 'post',
    data
  });
}

export function markPrivateRead(peerUserId: number) {
  return request({
    url: '/im/chat/private/read',
    method: 'put',
    params: { peerUserId }
  });
}
