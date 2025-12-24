import request from '@/utils/request'

// 查询班组设置列表
export function listTeam(query) {
  return request({
    url: '/sch/team/list',
    method: 'get',
    params: query
  })
}

// 查询班组设置详细
export function getTeam(teamId) {
  return request({
    url: '/sch/team/' + teamId,
    method: 'get'
  })
}

// 新增班组设置
export function addTeam(data) {
  return request({
    url: '/sch/team',
    method: 'post',
    data: data
  })
}

// 修改班组设置
export function updateTeam(data) {
  return request({
    url: '/sch/team',
    method: 'put',
    data: data
  })
}

// 删除班组设置
export function delTeam(teamId) {
  return request({
    url: '/sch/team/' + teamId,
    method: 'delete'
  })
}
