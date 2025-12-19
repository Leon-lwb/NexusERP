import request from '@/utils/request'

// 查询生产工人档案列表
export function listWorker(query) {
  return request({
    url: '/sch/worker/list',
    method: 'get',
    params: query
  })
}

// 查询生产工人档案详细
export function getWorker(workerId) {
  return request({
    url: '/sch/worker/' + workerId,
    method: 'get'
  })
}

// 新增生产工人档案
export function addWorker(data) {
  return request({
    url: '/sch/worker',
    method: 'post',
    data: data
  })
}

// 修改生产工人档案
export function updateWorker(data) {
  return request({
    url: '/sch/worker',
    method: 'put',
    data: data
  })
}

// 删除生产工人档案
export function delWorker(workerId) {
  return request({
    url: '/sch/worker/' + workerId,
    method: 'delete'
  })
}
