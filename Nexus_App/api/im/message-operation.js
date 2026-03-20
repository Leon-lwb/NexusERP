import request from '@/utils/request'

export function revokeMessage(messageId) {
  return request({
    url: `/im/message/revoke/${messageId}`,
    method: 'post'
  })
}

export function deleteMessage(messageId) {
  return request({
    url: `/im/message/delete/${messageId}`,
    method: 'delete'
  })
}

export function forwardMessage(data) {
  return request({
    url: '/im/message/forward',
    method: 'post',
    data
  })
}
