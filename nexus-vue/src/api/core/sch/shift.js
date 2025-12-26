import request from '@/utils/request'

// 查询班次定义列表
export function listShift(query) {
  return request({
    url: '/sch/shift/list',
    method: 'get',
    params: query
  })
}

// 查询班次定义详细
export function getShift(shiftId) {
  return request({
    url: '/sch/shift/' + shiftId,
    method: 'get'
  })
}

// 新增班次定义
export function addShift(data) {
  return request({
    url: '/sch/shift',
    method: 'post',
    data: data
  })
}

// 修改班次定义
export function updateShift(data) {
  return request({
    url: '/sch/shift',
    method: 'put',
    data: data
  })
}

// 删除班次定义
export function delShift(shiftId) {
  return request({
    url: '/sch/shift/' + shiftId,
    method: 'delete'
  })
}
