import request from '@/utils/request'

// 查询流程配置列表
export function listFlowConfig(query) {
  return request({
    url: '/workflow/flowConfig/list',
    method: 'get',
    params: query
  })
}

// 查询流程配置详细信息
export function getFlowConfig(id) {
  return request({
    url: '/workflow/flowConfig/' + id,
    method: 'get'
  })
}

// 查询流程节点配置详细信息
export function getConfigRule(query) {
  return request({
    headers: { 'datasource': localStorage.getItem("dataName") },
    url: '/workflow/flowConfig/getConfigRule',
    method: 'get',
    params: query
  })
}

// 更新修改流程规则两个子表
export function updateConfigRule(data) {
  return request({
    url: '/workflow/flowConfig/editConfigRule',
    method: 'post',
    data: data
  })
}

// 新增流程配置主
export function addFlowConfig(data) {
  return request({
    url: '/workflow/flowConfig',
    method: 'post',
    data: data
  })
}

// 修改流程配置主表
export function updateFlowConfig(data) {
  return request({
    url: '/workflow/flowConfig',
    method: 'put',
    data: data
  })
}

// 删除流程配置主表
export function delFlowConfig(id) {
  return request({
    url: '/workflow/flowConfig/' + id,
    method: 'delete'
  })
}
