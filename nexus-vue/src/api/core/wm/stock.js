import request from '@/utils/request'

// 查询库存清单列表
export function listStock(query) {
  return request({
    url: '/wm/stock/list',
    method: 'get',
    params: query
  })
}

// 查询库存清单详细
export function getStock(stockId) {
  return request({
    url: '/wm/stock/' + stockId,
    method: 'get'
  })
}

// 新增库存清单
export function addStock(data) {
  return request({
    url: '/wm/stock',
    method: 'post',
    data: data
  })
}

// 修改库存清单
export function updateStock(data) {
  return request({
    url: '/wm/stock',
    method: 'put',
    data: data
  })
}

// 删除库存清单
export function delStock(stockId) {
  return request({
    url: '/wm/stock/' + stockId,
    method: 'delete'
  })
}

// 获取指定分类及其子分类列表及库存数据
export function getCategoryStocks(categoryIds) {
  return request({
    url: '/wm/stock/categoryStocks/' + categoryIds,
    method: 'get'
  })
}