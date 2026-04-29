import request from '@/utils/request'

// 获取流程变量
export function getProcessVariables(taskId) {
  return request({
    url: '/workflow/task/processVariables/' + taskId,
    method: 'get'
  })
}

// 完成任务
export function complete(data) {
  return request({
    url: '/workflow/task/complete',
    method: 'post',
    data: data
  })
}

// 委派任务
export function delegate(data) {
  return request({
    url: '/workflow/task/delegate',
    method: 'post',
    data: data
  })
}

// 转办任务
export function transfer(data) {
  return request({
    url: '/workflow/task/transfer',
    method: 'post',
    data: data
  })
}

// 退回任务
export function returnTask(data) {
  return request({
    url: '/workflow/task/return',
    method: 'post',
    data: data
  })
}

// 驳回任务
export function rejectTask(data) {
  return request({
    url: '/workflow/task/reject',
    method: 'post',
    data: data
  })
}

// 拒绝任务
export function refuseTask(data) {
  return request({
    url: '/workflow/task/refuse',
    method: 'post',
    data: data
  })
}

// 加签任务
export function addSignTask(data) {
  return request({
    url: '/workflow/task/addSign',
    method: 'post',
    data: data
  })
}

// 多实例加签任务
export function multiInstanceAddSignTask(data) {
  return request({
    url: '/workflow/task/multiInstanceAddSign',
    method: 'post',
    data: data
  })
}

// 跳转任务
export function jumpTask(data) {
  return request({
    url: '/workflow/task/jump',
    method: 'post',
    data: data
  })
}

// 跳转任务节点列表
export function userTaskList(data) {
  return request({
    url: '/workflow/task/userTask',
    method: 'post',
    data: data
  })
}

// 签收任务
export function claimTask(data) {
  return request({
    url: '/workflow/task/claim',
    method: 'post',
    data: data
  })
}

// 可退回任务列表
export function returnList(data) {
  return request({
    url: '/workflow/task/returnList',
    method: 'post',
    data: data
  })
}
