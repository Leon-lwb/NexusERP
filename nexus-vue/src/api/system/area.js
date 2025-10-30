import request from '@/utils/request'

// 查询行政区划列表
export function listArea(query) {
  return request({
    url: '/system/area/list',
    method: 'get',
    params: query
  })
}

// 查询行政区划列表（排除节点）
export function listAreaExcludeChild(areaId) {
  return request({
    url: '/system/area/list/exclude/' + areaId,
    method: 'get'
  })
}

// 查询行政区划详细
export function getArea(pkid) {
  return request({
    url: '/system/area/' + pkid,
    method: 'get'
  })
}

// 新增行政区划
export function addArea(data) {
  return request({
    url: '/system/area',
    method: 'post',
    data: data
  })
}

// 修改行政区划
export function updateArea(data) {
  return request({
    url: '/system/area',
    method: 'put',
    data: data
  })
}

// 删除行政区划
export function delArea(pkid) {
  return request({
    url: '/system/area/' + pkid,
    method: 'delete'
  })
}