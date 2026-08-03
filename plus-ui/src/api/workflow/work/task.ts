import request from '@/utils/request';

// 获取流程变量
export function getProcessVariables(taskId?: any) {
  return request({
    url: '/workflow/task/processVariables/' + taskId,
    method: 'get'
  });
}

// 完成任务
export function complete(data?: any) {
  return request({
    url: '/workflow/task/complete',
    method: 'post',
    data: data
  });
}

// 委派任务
export function delegate(data?: any) {
  return request({
    url: '/workflow/task/delegate',
    method: 'post',
    data: data
  });
}

// 转办任务
export function transfer(data?: any) {
  return request({
    url: '/workflow/task/transfer',
    method: 'post',
    data: data
  });
}

// 退回任务
export function returnTask(data?: any) {
  return request({
    url: '/workflow/task/return',
    method: 'post',
    data: data
  });
}

// 驳回任务
export function rejectTask(data?: any) {
  return request({
    url: '/workflow/task/reject',
    method: 'post',
    data: data
  });
}

// 签收任务
export function claimTask(data?: any) {
  return request({
    url: '/workflow/task/claim',
    method: 'post',
    data: data
  });
}

// 可退回任务列表
export function returnList(data?: any) {
  return request({
    url: '/workflow/task/returnList',
    method: 'post',
    data: data
  });
}

// 撤回任务
export function revokeProcess(data?: any) {
  return request({
    url: '/workflow/task/revokeProcess',
    method: 'post',
    data: data
  });
}

// 拒绝任务
export function refuseTask(data?: any) {
  return request({
    url: '/workflow/task/refuse',
    method: 'post',
    data: data
  });
}

// 加签任务
export function addSignTask(data?: any) {
  return request({
    url: '/workflow/task/addSign',
    method: 'post',
    data: data
  });
}

// 多实例加签任务
export function multiInstanceAddSignTask(data?: any) {
  return request({
    url: '/workflow/task/multiInstanceAddSign',
    method: 'post',
    data: data
  });
}

// 跳转任务
export function jumpTask(data?: any) {
  return request({
    url: '/workflow/task/jump',
    method: 'post',
    data: data
  });
}

// 跳转任务节点列表
export function userTaskList(data?: any) {
  return request({
    url: '/workflow/task/userTask',
    method: 'post',
    data: data
  });
}

// 收回任务
export function recallProcess(data?: any) {
  return request({
    url: '/workflow/task/recallProcess',
    method: 'post',
    data: data
  });
}
