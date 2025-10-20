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

// 查询BOM明细列表
export function listBomDetails(bomId) {
  return request({
    url: '/bd/bom/detail/list',
    method: 'get',
    params: { bomId }
  })
}

// 获取BOM明细详细信息
export function getBomDetail(detailId) {
  return request({
    url: '/bd/bom/detail/' + detailId,
    method: 'get'
  })
}

// 新增BOM明细
export function addBomDetail(data) {
  return request({
    url: '/bd/bom/detail',
    method: 'post',
    data: data
  })
}

// 修改BOM明细
export function updateBomDetail(detailId, data) {
  return request({
    url: '/bd/bom/detail/' + detailId,
    method: 'put',
    data: data
  })
}

// 删除BOM明细
export function delBomDetail(detailIds) {
  return request({
    url: '/bd/bom/detail/' + detailIds,
    method: 'delete'
  })
}

// 批量保存BOM明细
export function saveBomDetailsBatch(data) {
  return request({
    url: '/bd/bom/detail/batch',
    method: 'post',
    data: data
  })
}

// 获取完整BOM结构
export function getBomStructure(parentId) {
  return request({
    url: '/bd/bom/structure/' + parentId,
    method: 'get'
  })
}

// 获取当前有效BOM
export function getCurrentBom(parentId) {
  return request({
    url: '/bd/bom/current/' + parentId,
    method: 'get'
  })
}