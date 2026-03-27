import request from '@/utils/request'

// 查询生产报工列表
export function listReport(query) {
  return request({
    url: '/prd/report/list',
    method: 'get',
    params: query
  })
}

// 查询生产报工详细
export function getReport(reportId) {
  return request({
    url: '/prd/report/' + reportId,
    method: 'get'
  })
}

// 新增生产报工
export function addReport(data) {
  return request({
    url: '/prd/report',
    method: 'post',
    data: data
  })
}

// 修改生产报工
export function updateReport(data) {
  return request({
    url: '/prd/report',
    method: 'put',
    data: data
  })
}

// 删除生产报工
export function delReport(reportId) {
  return request({
    url: '/prd/report/' + reportId,
    method: 'delete'
  })
}
