import request from '@/utils/request'

// 查询班组排班（模板）列表
export function listTeamSchedule(query) {
  return request({
    url: '/sch/teamSchedule/list',
    method: 'get',
    params: query
  })
}

// 查询班组排班（模板）详细
export function getTeamSchedule(teamScheduleId) {
  return request({
    url: '/sch/teamSchedule/' + teamScheduleId,
    method: 'get'
  })
}

// 新增班组排班（模板）
export function addTeamSchedule(data) {
  return request({
    url: '/sch/teamSchedule',
    method: 'post',
    data: data
  })
}

// 修改班组排班（模板）
export function updateTeamSchedule(data) {
  return request({
    url: '/sch/teamSchedule',
    method: 'put',
    data: data
  })
}

// 删除班组排班（模板）
export function delTeamSchedule(teamScheduleId) {
  return request({
    url: '/sch/teamSchedule/' + teamScheduleId,
    method: 'delete'
  })
}
