import request from '@/utils/request'

// 查询物料入库单列表
export function listReceive(query) {
  return request({
    url: '/wm/receive/list',
    method: 'get',
    params: query
  })
}

// 查询物料入库单详细
export function getReceive(receiveId) {
  return request({
    url: '/wm/receive/' + receiveId,
    method: 'get'
  })
}

// 新增物料入库单
export function addReceive(data) {
  return request({
    url: '/wm/receive',
    method: 'post',
    data: data
  })
}

// 修改物料入库单
export function updateReceive(data) {
  return request({
    url: '/wm/receive',
    method: 'put',
    data: data
  })
}

// 删除物料入库单
export function delReceive(receiveId) {
  return request({
    url: '/wm/receive/' + receiveId,
    method: 'delete'
  })
}

// 执行物料入库
export function execute(receiveId, data) {
  return request({
    url: '/wm/receive/execute/' + receiveId,
    method: 'post',
    data: data
  })
}
