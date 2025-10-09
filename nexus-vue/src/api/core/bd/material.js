import request from '@/utils/request'

// 查询物料产品主列表
export function listMaterial(query) {
  return request({
    url: '/bd/material/list',
    method: 'get',
    params: query
  })
}

// 查询物料产品主详细
export function getMaterial(materialId) {
  return request({
    url: '/bd/material/' + materialId,
    method: 'get'
  })
}

// 新增物料产品主
export function addMaterial(data) {
  return request({
    url: '/bd/material',
    method: 'post',
    data: data
  })
}

// 修改物料产品主
export function updateMaterial(data) {
  return request({
    url: '/bd/material',
    method: 'put',
    data: data
  })
}

// 删除物料产品主
export function delMaterial(materialId) {
  return request({
    url: '/bd/material/' + materialId,
    method: 'delete'
  })
}
