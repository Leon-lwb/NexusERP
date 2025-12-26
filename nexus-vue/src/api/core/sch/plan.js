import request from '@/utils/request'

// 查询排班计划列表
export function listPlan(query) {
  return request({
    url: '/sch/plan/list',
    method: 'get',
    params: query
  })
}

// 查询排班计划详细
export function getPlan(planId) {
  return request({
    url: '/sch/plan/' + planId,
    method: 'get'
  })
}

// 新增排班计划
export function addPlan(data) {
  return request({
    url: '/sch/plan',
    method: 'post',
    data: data
  })
}

// 修改排班计划
export function updatePlan(data) {
  return request({
    url: '/sch/plan',
    method: 'put',
    data: data
  })
}

// 删除排班计划
export function delPlan(planId) {
  return request({
    url: '/sch/plan/' + planId,
    method: 'delete'
  })
}
