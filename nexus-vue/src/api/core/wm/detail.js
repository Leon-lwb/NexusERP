import request from '@/utils/request'

// 查询物料入库单明细列表
export function listDetail(query) {
  return request({
    url: '/wm/detail/list',
    method: 'get',
    params: query
  })
}

// 查询物料入库单明细详细
export function getDetail(detailId) {
  return request({
    url: '/wm/detail/' + detailId,
    method: 'get'
  })
}

// 新增物料入库单明细
export function addDetail(data) {
  return request({
    url: '/wm/detail',
    method: 'post',
    data: data
  })
}

// 修改物料入库单明细
export function updateDetail(data) {
  return request({
    url: '/wm/detail',
    method: 'put',
    data: data
  })
}

// 删除物料入库单明细
export function delDetail(detailId) {
  return request({
    url: '/wm/detail/' + detailId,
    method: 'delete'
  })
}
