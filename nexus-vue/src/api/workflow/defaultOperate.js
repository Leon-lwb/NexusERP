import request from '@/utils/request'

// 查询流程默认操作列表
export function listDefaultOperate(query) {
  return request({
    url: '/workflow/defaultOperate/list',
    method: 'get',
    params: query
  })
}

// 查询流程默认操作详细
export function getDefaultOperate(id) {
  return request({
    url: '/workflow/defaultOperate/' + id,
    method: 'get'
  })
}

// 新增流程默认操作
export function addDefaultOperate(data) {
  return request({
    url: '/workflow/defaultOperate',
    method: 'post',
    data: data
  })
}

// 修改流程默认操作
export function updateDefaultOperate(data) {
  return request({
    url: '/workflow/defaultOperate',
    method: 'put',
    data: data
  })
}

// 删除流程默认操作
export function delDefaultOperate(id) {
  return request({
    url: '/workflow/defaultOperate/' + id,
    method: 'delete'
  })
}
