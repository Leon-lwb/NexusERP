import request from '@/utils/request'

// 查询计划班组关联列表
export function listPlanteam(query) {
  return request({
    url: '/sch/planteam/list',
    method: 'get',
    params: query
  })
}

// 查询计划班组关联详细
export function getPlanteam(recordId) {
  return request({
    url: '/sch/planteam/' + recordId,
    method: 'get'
  })
}

// 新增计划班组关联
export function addPlanteam(data) {
  return request({
    url: '/sch/planteam',
    method: 'post',
    data: data
  })
}

// 修改计划班组关联
export function updatePlanteam(data) {
  return request({
    url: '/sch/planteam',
    method: 'put',
    data: data
  })
}

// 删除计划班组关联
export function delPlanteam(recordId) {
  return request({
    url: '/sch/planteam/' + recordId,
    method: 'delete'
  })
}
