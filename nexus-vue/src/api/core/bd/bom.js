import request from '@/utils/request'

// 查询产品BOM列表
export function listBom(query) {
  return request({
    url: '/bd/bom/list',
    method: 'get',
    params: query
  })
}

// 查询产品BOM详细
export function getBom(bomId) {
  return request({
    url: '/bd/bom/' + bomId,
    method: 'get'
  })
}

// 新增产品BOM
export function addBom(data) {
  return request({
    url: '/bd/bom',
    method: 'post',
    data: data
  })
}

// 修改产品BOM
export function updateBom(data) {
  return request({
    url: '/bd/bom',
    method: 'put',
    data: data
  })
}

// 删除产品BOM
export function delBom(bomId) {
  return request({
    url: '/bd/bom/' + bomId,
    method: 'delete'
  })
}
