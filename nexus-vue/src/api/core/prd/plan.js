import request from '@/utils/request'

// 查询生产计划列表
export function listPlan(query) {
  return request({
    url: '/prd/plan/list',
    method: 'get',
    params: query
  })
}

// 查询生产计划详细
export function getPlan(planId) {
  return request({
    url: '/prd/plan/' + planId,
    method: 'get'
  })
}

// 新增生产计划
export function addPlan(data) {
  return request({
    url: '/prd/plan',
    method: 'post',
    data: data
  })
}

// 修改生产计划
export function updatePlan(data) {
  return request({
    url: '/prd/plan',
    method: 'put',
    data: data
  })
}

// 删除生产计划
export function delPlan(planId) {
  return request({
    url: '/prd/plan/' + planId,
    method: 'delete'
  })
}
