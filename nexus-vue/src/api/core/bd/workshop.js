import request from '@/utils/request'

// 查询车间设置列表
export function listWorkshop(query) {
  return request({
    url: '/bd/workshop/list',
    method: 'get',
    params: query
  })
}

// 查询车间设置详细
export function getWorkshop(workshopId) {
  return request({
    url: '/bd/workshop/' + workshopId,
    method: 'get'
  })
}

// 新增车间设置
export function addWorkshop(data) {
  return request({
    url: '/bd/workshop',
    method: 'post',
    data: data
  })
}

// 修改车间设置
export function updateWorkshop(data) {
  return request({
    url: '/bd/workshop',
    method: 'put',
    data: data
  })
}

// 删除车间设置
export function delWorkshop(workshopId) {
  return request({
    url: '/bd/workshop/' + workshopId,
    method: 'delete'
  })
}
