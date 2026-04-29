import request from '@/utils/request'

// 查询流程操作规则列表
export function listOperateRule(query) {
  return request({
    url: '/workflow/operateRule/list',
    method: 'get',
    params: query
  })
}

// 查询流程操作规则详细
export function getOperateRule(id) {
  return request({
    url: '/workflow/operateRule/' + id,
    method: 'get'
  })
}

// 新增流程操作规则
export function addOperateRule(data) {
  return request({
    url: '/workflow/operateRule',
    method: 'post',
    data: data
  })
}

// 修改流程操作规则
export function updateOperateRule(data) {
  return request({
    url: '/workflow/operateRule',
    method: 'put',
    data: data
  })
}

// 删除流程操作规则
export function delOperateRule(id) {
  return request({
    url: '/workflow/operateRule/' + id,
    method: 'delete'
  })
}
