import request from '@/utils/request';
import { AxiosPromise } from 'axios';

export function listConversations(): AxiosPromise<any[]> {
  return request({
    url: '/im/chat/conversations',
    method: 'get'
  });
}

export function sendPrivateMessage(data: { toUserId: number | string; content: string; msgType?: string; forwardFrom?: number | string }) {
  return request({
    url: '/im/chat/private/send',
    method: 'post',
    data
  });
}

export function sendGroupMessage(data: { groupId: string | number; content: string; msgType?: string; forwardFrom?: number | string }) {
  return request({
    url: '/im/chat/group/send',
    method: 'post',
    data
  });
}

export function privateHistory(params: { peerUserId: number | string; pageNum?: number; pageSize?: number }) {
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

/** 私聊历史（带发送者名称/头像），消息行含 senderName / senderAvatar */
export function privateHistoryWithSender(params: { peerUserId: number | string; pageNum?: number; pageSize?: number }) {
  return request({
    url: '/im/chat/private/history/withSender',
    method: 'get',
    params
  });
}

/** 群聊历史（带发送者名称/头像），消息行含 senderName / senderAvatar */
export function groupHistoryWithSender(params: { groupId: string | number; pageNum?: number; pageSize?: number }) {
  return request({
    url: '/im/chat/group/history/withSender',
    method: 'get',
    params
  });
}

export function createGroup(data: { groupName: string; avatar?: string; memberUserIds: (number | string)[] }) {
  return request({
    url: '/im/chat/group',
    method: 'post',
    data
  });
}

export function addGroupMembers(groupId: string | number, data: { memberUserIds: (number | string)[] }) {
  return request({
    url: '/im/chat/group/' + groupId + '/members',
    method: 'post',
    data
  });
}

export function markPrivateRead(peerUserId: number | string) {
  return request({
    url: '/im/chat/private/read',
    method: 'put',
    params: { peerUserId }
  });
}

/** 群消息标记已读 */
export function markGroupRead(groupId: number | string) {
  return request({
    url: '/im/chat/group/read',
    method: 'put',
    params: { groupId }
  });
}

/** 群成员列表（含用户名称/头像/是否群主） */
export function listGroupMembers(groupId: number | string): AxiosPromise<any[]> {
  return request({
    url: '/im/chat/group/' + groupId + '/members',
    method: 'get'
  });
}

/** 群主移除群成员 */
export function removeGroupMember(groupId: number | string, userId: number | string) {
  return request({
    url: '/im/chat/group/' + groupId + '/members/' + userId,
    method: 'delete'
  });
}

/** 撤回自己的消息（2 分钟内） */
export function revokeMessage(messageId: number | string) {
  return request({
    url: '/im/chat/message/' + messageId + '/revoke',
    method: 'put'
  });
}

/** 上传文件到 OSS，返回 { fileName, url, ossId } */
export function uploadOss(file: File): AxiosPromise<{ fileName: string; url: string; ossId: string | number }> {
  const formData = new FormData();
  formData.append('file', file);
  return request({
    url: '/resource/oss/upload',
    method: 'post',
    data: formData
  });
}
