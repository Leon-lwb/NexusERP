import request from '@/utils/request'

export function createGroup(data) {
  return request({
    url: '/im/group/create',
    method: 'post',
    data
  })
}

export function getGroupList() {
  return request({
    url: '/im/group/list',
    method: 'get'
  })
}

export function getGroupInfo(groupId) {
  return request({
    url: '/im/group/info',
    method: 'get',
    params: { groupId }
  })
}

export function getGroupMembers(groupId) {
  return request({
    url: '/im/group/members',
    method: 'get',
    params: { groupId }
  })
}

export function addGroupMembers(data) {
  return request({
    url: '/im/group/add-members',
    method: 'post',
    data
  })
}

export function removeGroupMember(data) {
  return request({
    url: '/im/group/remove-member',
    method: 'post',
    data
  })
}

export function updateGroupInfo(data) {
  return request({
    url: '/im/group/update',
    method: 'post',
    data
  })
}

export function quitGroup(data) {
  return request({
    url: '/im/group/quit',
    method: 'post',
    data
  })
}

export function disbandGroup(data) {
  return request({
    url: '/im/group/disband',
    method: 'post',
    data
  })
}
