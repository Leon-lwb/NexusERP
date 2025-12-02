import request from '@/utils/request'

// 查询库位列表
export function listStoragelocation(query) {
  return request({
    url: '/wm/storagelocation/list',
    method: 'get',
    params: query
  })
}

// 查询库位详细
export function getStoragelocation(locationId) {
  return request({
    url: '/wm/storagelocation/' + locationId,
    method: 'get'
  })
}

// 新增库位
export function addStoragelocation(data) {
  return request({
    url: '/wm/storagelocation',
    method: 'post',
    data: data
  })
}

// 修改库位
export function updateStoragelocation(data) {
  return request({
    url: '/wm/storagelocation',
    method: 'put',
    data: data
  })
}

// 删除库位
export function delStoragelocation(locationId) {
  return request({
    url: '/wm/storagelocation/' + locationId,
    method: 'delete'
  })
}
