import request from '@/utils/request'

// 查询生产工单资源分配列表
export function listWorkOrderResource(query) {
  return request({
    url: '/prd/workOrderResource/list',
    method: 'get',
    params: query
  })
}

// 查询生产工单资源分配详细
export function getWorkOrderResource(id) {
  return request({
    url: '/prd/workOrderResource/' + id,
    method: 'get'
  })
}

// 新增生产工单资源分配
export function addWorkOrderResource(data) {
  return request({
    url: '/prd/workOrderResource',
    method: 'post',
    data: data
  })
}

// 修改生产工单资源分配
export function updateWorkOrderResource(data) {
  return request({
    url: '/prd/workOrderResource',
    method: 'put',
    data: data
  })
}

// 删除生产工单资源分配
export function delWorkOrderResource(id) {
  return request({
    url: '/prd/workOrderResource/' + id,
    method: 'delete'
  })
}

// 根据工单ID查询资源分配列表
export function getWorkOrderResourceByWoId(woId) {
  return request({
    url: '/prd/workOrderResource/wo/' + woId,
    method: 'get'
  })
}

// 根据工单ID和资源类型查询资源分配列表
export function getWorkOrderResourceByWoIdAndType(woId, resourceType) {
  return request({
    url: '/prd/workOrderResource/wo/' + woId + '/type/' + resourceType,
    method: 'get'
  })
}

// 批量保存工单资源分配
export function saveWorkOrderResourceBatch(woId, resourceList) {
  return request({
    url: '/prd/workOrderResource/batch/' + woId,
    method: 'post',
    data: resourceList
  })
}

// 资源类型常量
export const RESOURCE_TYPE = {
  WORKSHOP: 'WORKSHOP',
  WORKSTATION: 'WORKSTATION',
  EQUIPMENT: 'EQUIPMENT',
  TEAM: 'TEAM',
  PRODUCTION_LINE: 'PRODUCTION_LINE'
}

// 资源类型标签
export const RESOURCE_TYPE_LABEL = {
  WORKSHOP: '车间',
  WORKSTATION: '工作站',
  EQUIPMENT: '设备',
  TEAM: '班组',
  PRODUCTION_LINE: '生产线'
}

// 状态常量
export const RESOURCE_STATUS = {
  PENDING: 'PENDING',
  PROCESSING: 'PROCESSING',
  COMPLETED: 'COMPLETED'
}

// 状态标签
export const RESOURCE_STATUS_LABEL = {
  PENDING: '待执行',
  PROCESSING: '执行中',
  COMPLETED: '已完成'
}
