import request from '@/utils/request'

// 查询库区列表
export function listStoragearea(query) {
  return request({
    url: '/wm/storagearea/list',
    method: 'get',
    params: query
  })
}

// 查询库区详细
export function getStoragearea(areaId) {
  return request({
    url: '/wm/storagearea/' + areaId,
    method: 'get'
  })
}

// 新增库区
export function addStoragearea(data) {
  return request({
    url: '/wm/storagearea',
    method: 'post',
    data: data
  })
}

// 修改库区
export function updateStoragearea(data) {
  return request({
    url: '/wm/storagearea',
    method: 'put',
    data: data
  })
}

// 删除库区
export function delStoragearea(areaId) {
  return request({
    url: '/wm/storagearea/' + areaId,
    method: 'delete'
  })
}
