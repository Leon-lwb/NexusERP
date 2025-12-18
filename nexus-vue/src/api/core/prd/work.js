import request from '@/utils/request'

// 查询生产工单列表
export function listWork(query) {
  return request({
    url: '/prd/work/list',
    method: 'get',
    params: query
  })
}

// 查询生产工单详细
export function getWork(woId) {
  return request({
    url: '/prd/work/' + woId,
    method: 'get'
  })
}

// 新增生产工单
export function addWork(data) {
  return request({
    url: '/prd/work',
    method: 'post',
    data: data
  })
}

// 修改生产工单
export function updateWork(data) {
  return request({
    url: '/prd/work',
    method: 'put',
    data: data
  })
}

// 删除生产工单
export function delWork(woId) {
  return request({
    url: '/prd/work/' + woId,
    method: 'delete'
  })
}
