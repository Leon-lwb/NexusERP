import request from '@/utils/request'

export function sendFriendRequest(data) {
  return request({
    url: '/im/friend/request/send',
    method: 'post',
    data
  })
}

export function handleFriendRequest(data) {
  return request({
    url: '/im/friend/request/handle',
    method: 'post',
    data
  })
}

export function getReceivedRequests() {
  return request({
    url: '/im/friend/request/received',
    method: 'get'
  })
}

export function getSentRequests() {
  return request({
    url: '/im/friend/request/sent',
    method: 'get'
  })
}

export function getUnhandledCount() {
  return request({
    url: '/im/friend/request/unhandled-count',
    method: 'get'
  })
}

export function revokeFriendRequest(messageId) {
  return request({
    url: `/im/friend/request/${messageId}`,
    method: 'delete'
  })
}

export function checkPendingRequest(toUserId) {
  return request({
    url: '/im/friend/request/check',
    method: 'get',
    params: { toUserId }
  })
}
