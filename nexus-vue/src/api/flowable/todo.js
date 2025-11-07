import request from '@/utils/request';
import { getAction, postAction, deleteAction } from '@/utils/request';

// 查询待办任务列表
export function todoList(query) {
  return getAction('/flowable/task/todoList', query)
}

// 完成任务
export function complete(data) {
  return postAction('/flowable/task/complete', data)
}

// 委派任务
export function delegate(data) {
  return postAction('/flowable/task/delegate', data)
}

// 退回任务
export function returnTask(data) {
  return postAction('/flowable/task/return', data)
}

// 驳回任务
export function rejectTask(data) {
  return postAction('/flowable/task/reject', data)
}

// 可退回任务列表
export function returnList(data) {
  return postAction('/flowable/task/returnList', data)
}

// 下一节点
export function getNextFlowNode(data) {
  return postAction('/flowable/task/nextFlowNode', data)
}

// 下一节点
export function getNextFlowNodeByStart(data) {
  return postAction('/flowable/task/nextFlowNodeByStart', data)
}

// 部署流程实例
export function deployStart(deployId) {
  return getAction('/flowable/process/startFlow/' + deployId)
}

// 删除流程定义
export function delDeployment(id) {
  return deleteAction('/flowable/task/delete', { taskId: id })
}

// 流程节点表单
export function flowTaskForm(query) {
  return getAction('/flowable/task/flowTaskForm', query)
}
