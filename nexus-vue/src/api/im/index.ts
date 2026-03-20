import request from '@/utils/request'

export function loginToIm() {
  return request({
    url: '/im/login',
    method: 'post'
  })
}

export function logoutFromIm() {
  return request({
    url: '/im/logout',
    method: 'post'
  })
}

export function getUserImInfo() {
  return request({
    url: '/im/user/info',
    method: 'get'
  })
}

export function getImUserList() {
  return request({
    url: '/im/user/list',
    method: 'get'
  })
}

export function getUserListForIm(params?: any) {
  return request({
    url: '/system/user/list',
    method: 'get',
    params
  })
}

export function sendMessage(data: any) {
  return request({
    url: '/im/message/send',
    method: 'post',
    data
  })
}

export function sendGroupMessage(data: any) {
  return request({
    url: '/im/message/send-group',
    method: 'post',
    data
  })
}

export function getMessageHistory(params: any) {
  return request({
    url: '/im/message/history',
    method: 'get',
    params
  })
}

export function getConversationList() {
  return request({
    url: '/im/conversation/list',
    method: 'get'
  })
}

export function markMessagesAsRead(toUserId?: number, groupId?: string) {
  return request({
    url: '/im/message/read',
    method: 'post',
    data: {
      toUserId,
      groupId
    }
  })
}

export function createGroup(data: any) {
  return request({
    url: '/im/group/create',
    method: 'post',
    data
  })
}

export function getUserGroups() {
  return request({
    url: '/im/group/list',
    method: 'get'
  })
}

export function getGroupList() {
  return request({
    url: '/im/group/list',
    method: 'get'
  })
}

export function getGroupInfo(groupId: string) {
  return request({
    url: '/im/group/info',
    method: 'get',
    params: { groupId }
  })
}

export function getGroupMembers(groupId: string) {
  return request({
    url: '/im/group/members',
    method: 'get',
    params: { groupId }
  })
}

export function addGroupMembers(data: any) {
  return request({
    url: '/im/group/add-members',
    method: 'post',
    data
  })
}

export function removeGroupMember(data: any) {
  return request({
    url: '/im/group/remove-member',
    method: 'post',
    data
  })
}

export function updateGroupInfo(data: any) {
  return request({
    url: '/im/group/update',
    method: 'post',
    data
  })
}

export function quitGroup(data: any) {
  return request({
    url: '/im/group/quit',
    method: 'post',
    data
  })
}

export function disbandGroup(data: any) {
  return request({
    url: '/im/group/disband',
    method: 'post',
    data
  })
}

export function checkImTables() {
  return request({
    url: '/im/check-tables',
    method: 'get'
  })
}

export function testIm() {
  return request({
    url: '/im/test',
    method: 'get'
  })
}

export function addFriend(data: any) {
  return request({
    url: '/im/friend/add',
    method: 'post',
    data
  })
}

export function getFriendList() {
  return request({
    url: '/im/friend/list',
    method: 'get'
  })
}

export function removeFriend(friendUserId: number) {
  return request({
    url: '/im/friend/remove',
    method: 'post',
    data: { friendUserId }
  })
}

export function getUnreadCount() {
  return request({
    url: '/im/message/unread-count',
    method: 'get'
  })
}

export function getUnreadMessages() {
  return request({
    url: '/im/message/unread',
    method: 'get'
  })
}

export function getGroups() {
  return request({
    url: '/im/group/list',
    method: 'get'
  })
}
