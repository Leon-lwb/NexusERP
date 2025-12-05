import request from '@/utils/request'

// 查询到货通知列表
export function listArrivalnotice(query) {
  return request({
    url: '/wm/arrivalnotice/list',
    method: 'get',
    params: query
  })
}

// 查询到货通知详细
export function getArrivalnotice(noticeId) {
  return request({
    url: '/wm/arrivalnotice/' + noticeId,
    method: 'get'
  })
}

// 新增到货通知
export function addArrivalnotice(data) {
  return request({
    url: '/wm/arrivalnotice',
    method: 'post',
    data: data
  })
}

// 修改到货通知
export function updateArrivalnotice(data) {
  return request({
    url: '/wm/arrivalnotice',
    method: 'put',
    data: data
  })
}

// 删除到货通知
export function delArrivalnotice(noticeId) {
  return request({
    url: '/wm/arrivalnotice/' + noticeId,
    method: 'delete'
  })
}
