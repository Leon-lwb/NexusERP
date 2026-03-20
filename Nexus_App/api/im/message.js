import request from '@/utils/request'

export function sendMessage(data) {
  return request({
    url: '/im/message/send',
    method: 'post',
    data
  })
}

export function sendGroupMessage(data) {
  return request({
    url: '/im/message/send-group',
    method: 'post',
    data
  })
}

export function getMessageHistory(params) {
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

export function markMessagesAsRead(data) {
  return request({
    url: '/im/message/read',
    method: 'post',
    data
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
