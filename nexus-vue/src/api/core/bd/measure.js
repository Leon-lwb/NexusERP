import request from '@/utils/request'

// 查询计量单位列表
export function listMeasure(query) {
  return request({
    url: '/bd/measure/list',
    method: 'get',
    params: query
  })
}

// 查询计量单位详细
export function getMeasure(uomId) {
  return request({
    url: '/bd/measure/' + uomId,
    method: 'get'
  })
}

// 新增计量单位
export function addMeasure(data) {
  return request({
    url: '/bd/measure',
    method: 'post',
    data: data
  })
}

// 修改计量单位
export function updateMeasure(data) {
  return request({
    url: '/bd/measure',
    method: 'put',
    data: data
  })
}

// 删除计量单位
export function delMeasure(uomId) {
  return request({
    url: '/bd/measure/' + uomId,
    method: 'delete'
  })
}
