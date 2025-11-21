import request from '@/utils/request'

// 查询物料清单列表
export function listRequirement(query) {
  return request({
    url: '/prd/requirement/list',
    method: 'get',
    params: query
  })
}

// 查询物料清单详细
export function getRequirement(requirementId) {
  return request({
    url: '/prd/requirement/' + requirementId,
    method: 'get'
  })
}

// 新增物料清单
export function addRequirement(data) {
  return request({
    url: '/prd/requirement',
    method: 'post',
    data: data
  })
}

// 修改物料清单
export function updateRequirement(data) {
  return request({
    url: '/prd/requirement',
    method: 'put',
    data: data
  })
}

// 删除物料清单
export function delRequirement(requirementId) {
  return request({
    url: '/prd/requirement/' + requirementId,
    method: 'delete'
  })
}
