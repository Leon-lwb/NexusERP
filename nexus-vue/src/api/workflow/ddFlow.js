import request from '@/utils/request'

// 查询钉钉流程列表
export function listDdFlow(query) {
  return request({
    url: '/workflow/ddFlow/list',
    method: 'get',
    params: query
  })
}

// 查询钉钉流程详细
export function getDdFlow(id) {
  return request({
    url: '/workflow/ddFlow/' + id,
    method: 'get'
  })
}

// 新增钉钉流程
export function addDdFlow(data) {
  return request({
    url: '/workflow/ddFlow',
    method: 'post',
    data: data
  })
}

// 修改钉钉流程
export function updateDdFlow(data) {
  return request({
    url: '/workflow/ddFlow',
    method: 'put',
    data: data
  })
}

// 删除钉钉流程
export function delDdFlow(id) {
  return request({
    url: '/workflow/ddFlow/' + id,
    method: 'delete'
  })
}
