import request from '@/utils/request'

// 查询客户信息列表
export function listBd(query) {
  return request({
    url: '/core/bd/list',
    method: 'get',
    params: query
  })
}

// 查询客户信息详细
export function getBd(clientId) {
  return request({
    url: '/core/bd/' + clientId,
    method: 'get'
  })
}

// 新增客户信息
export function addBd(data) {
  return request({
    url: '/core/bd',
    method: 'post',
    data: data
  })
}

// 修改客户信息
export function updateBd(data) {
  return request({
    url: '/core/bd',
    method: 'put',
    data: data
  })
}

// 删除客户信息
export function delBd(clientId) {
  return request({
    url: '/core/bd/' + clientId,
    method: 'delete'
  })
}
