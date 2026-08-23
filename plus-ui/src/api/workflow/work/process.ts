import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ProcessQuery, ProcessVO } from '@/api/workflow/work/types';

// 查询流程列表
export function listProcess(query?: ProcessQuery): AxiosPromise<ProcessVO[]> {
  return request({
    url: '/workflow/process/list',
    method: 'get',
    params: query
  });
}

// 查询流程列表
export function getProcessForm(query?: any) {
  return request({
    url: '/workflow/process/getProcessForm',
    method: 'get',
    params: query
  });
}

// 部署流程实例
export function startProcess(processDefId?: string, data?: string) {
  return request({
    url: '/workflow/process/start/' + processDefId,
    method: 'post',
    data: data
  });
}

// 删除流程实例
export function delProcess(ids?: string | Array<string | number>) {
  return request({
    url: '/workflow/process/instance/' + ids,
    method: 'delete'
  });
}

// 获取流程图
export function getBpmnXml(processDefId?: string) {
  return request({
    url: '/workflow/process/bpmnXml/' + processDefId,
    method: 'get'
  });
}

export function detailProcess(query?: any) {
  return request({
    url: '/workflow/process/detail',
    method: 'get',
    params: query
  });
}

// 我的发起的流程
export function listOwnProcess(query?: any) {
  return request({
    url: '/workflow/process/ownList',
    method: 'get',
    params: query
  });
}

// 我待办的流程
export function listTodoProcess(query?: any) {
  return request({
    url: '/workflow/process/todoList',
    method: 'get',
    params: query
  });
}

// 我待签的流程
export function listClaimProcess(query?: any) {
  return request({
    url: '/workflow/process/claimList',
    method: 'get',
    params: query
  });
}

// 我已办的流程
export function listFinishedProcess(query?: any) {
  return request({
    url: '/workflow/process/finishedList',
    method: 'get',
    params: query
  });
}

// 查询流程抄送列表
export function listCopyProcess(query?: any) {
  return request({
    url: '/workflow/process/copyList',
    method: 'get',
    params: query
  });
}

//抄送人已读状态
export function updateCcReaded(parameter?: any) {
  return request({
    url: '/workflow/process/updateViewStatust',
    method: 'get',
    params: parameter
  });
}

// 取消申请
export function stopProcess(data?: any) {
  return request({
    url: '/workflow/task/stopProcess',
    method: 'post',
    data: data
  });
}

//根据当前服务名,组织与站点查询关联流程信息
export function getProcesss(query?: any) {
  return request({
    url: '/workflow/process/getProcesss',
    method: 'get',
    params: query
  });
}

// 删除自定义业务任务关联表与流程历史表
//以便可以重新发起流程,业务数据dataid

export function deleteActivityAndJoin(dataId?: any, data?: any) {
  return request({
    url: '/workflow/task/deleteActivityAndJoin/' + dataId,
    method: 'post',
    data: data
  });
}
