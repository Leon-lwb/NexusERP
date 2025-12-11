import request from '@/utils/request'

// 查询仓库设置列表
export function listWarehouse(query) {
  return request({
    url: '/wm/warehouse/list',
    method: 'get',
    params: query
  })
}

// 查询仓库设置详细
export function getWarehouse(warehouseId) {
  return request({
    url: '/wm/warehouse/' + warehouseId,
    method: 'get'
  })
}

// 获取仓库树形结构
export function getWareHouseTree() {
  return request({
    url: '/wm/warehouse/getWareHouseTree',
    method: 'get'
  })
}

// 新增仓库设置
export function addWarehouse(data) {
  return request({
    url: '/wm/warehouse',
    method: 'post',
    data: data
  })
}

// 修改仓库设置
export function updateWarehouse(data) {
  return request({
    url: '/wm/warehouse',
    method: 'put',
    data: data
  })
}

// 删除仓库设置
export function delWarehouse(warehouseId) {
  return request({
    url: '/wm/warehouse/' + warehouseId,
    method: 'delete'
  })
}
