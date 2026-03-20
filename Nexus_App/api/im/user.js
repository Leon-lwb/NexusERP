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

export function addFriend(data) {
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

export function removeFriend(friendUserId) {
  return request({
    url: '/im/friend/remove',
    method: 'post',
    data: { friendUserId }
  })
}
