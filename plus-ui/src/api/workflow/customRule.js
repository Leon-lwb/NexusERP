import request from '@/utils/request'

// 查询流程自定义业务规则列表
export function listCustomRule(query) {
  return request({
    url: '/workflow/customRule/list',
    method: 'get',
    params: query
  })
}

// 查询流程自定义业务规则详细
export function getCustomRule(id) {
  return request({
    url: '/workflow/customRule/' + id,
    method: 'get'
  })
}

// 新增流程自定义业务规则
export function addCustomRule(data) {
  return request({
    url: '/workflow/customRule',
    method: 'post',
    data: data
  })
}

// 修改流程自定义业务规则
export function updateCustomRule(data) {
  return request({
    url: '/workflow/customRule',
    method: 'put',
    data: data
  })
}

// 删除流程自定义业务规则
export function delCustomRule(id) {
  return request({
    url: '/workflow/customRule/' + id,
    method: 'delete'
  })
}
