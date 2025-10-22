import request from '@/utils/request'

// 查询销售订单列表
export function listOrderheader(query) {
  return request({
    url: '/so/orderheader/list',
    method: 'get',
    params: query
  })
}

// 查询销售订单详细
export function getOrderheader(soId) {
  return request({
    url: '/so/orderheader/' + soId,
    method: 'get'
  })
}

// 新增销售订单
export function addOrderheader(data) {
  return request({
    url: '/so/orderheader',
    method: 'post',
    data: data
  })
}

// 修改销售订单
export function updateOrderheader(data) {
  return request({
    url: '/so/orderheader',
    method: 'put',
    data: data
  })
}

// 删除销售订单
export function delOrderheader(soId) {
  return request({
    url: '/so/orderheader/' + soId,
    method: 'delete'
  })
}
