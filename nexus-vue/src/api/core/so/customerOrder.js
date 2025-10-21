import request from '@/utils/request'

// 查询客户订单列表
export function listCustomerOrder(query) {
  return request({
    url: '/so/customerOrder/list',
    method: 'get',
    params: query
  })
}

// 查询客户订单详细
export function getCustomerOrder(coId) {
  return request({
    url: '/so/customerOrder/' + coId,
    method: 'get'
  })
}

// 新增客户订单
export function addCustomerOrder(data) {
  return request({
    url: '/so/customerOrder',
    method: 'post',
    data: data
  })
}

// 修改客户订单
export function updateCustomerOrder(data) {
  return request({
    url: '/so/customerOrder',
    method: 'put',
    data: data
  })
}

// 删除客户订单
export function delCustomerOrder(coId) {
  return request({
    url: '/so/customerOrder/' + coId,
    method: 'delete'
  })
}
