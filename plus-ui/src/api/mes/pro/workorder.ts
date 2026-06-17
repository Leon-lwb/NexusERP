import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ProWorkOrderVO, ProWorkOrderForm, ProWorkOrderQuery } from './types';

/** 分页查询MES工单列表 */
export const listWorkOrder = (query?: ProWorkOrderQuery): AxiosPromise<ProWorkOrderVO[]> => {
  return request({
    url: '/mes/pro/work/list',
    method: 'get',
    params: query
  });
};

/** 查询MES工单详情 */
export const getWorkOrder = (mesWoId: string | number): AxiosPromise<ProWorkOrderVO> => {
  return request({
    url: `/mes/pro/work/${mesWoId}`,
    method: 'get'
  });
};

/** 新增MES工单 */
export const addWorkOrder = (data: ProWorkOrderForm) => {
  return request({
    url: '/mes/pro/work',
    method: 'post',
    data: data
  });
};

/** 修改MES工单 */
export const updateWorkOrder = (data: ProWorkOrderForm) => {
  return request({
    url: '/mes/pro/work',
    method: 'put',
    data: data
  });
};

/** 删除MES工单 */
export const delWorkOrder = (mesWoIds: string | number | string[] | number[]) => {
  return request({
    url: `/mes/pro/work/${mesWoIds}`,
    method: 'delete'
  });
};

/** 下达MES工单 */
export const releaseWorkOrder = (mesWoId: string | number) => {
  return request({
    url: `/mes/pro/work/release/${mesWoId}`,
    method: 'put'
  });
};

/** 提交审批 */
export const submitWorkOrderFlow = (mesWoId: string | number, selectFlowId?: string) => {
  return request({
    url: `/mes/pro/work/${mesWoId}/submit`,
    method: 'post',
    params: { selectFlowId }
  });
};

/** 查询流程实例ID */
export const getWorkOrderProcId = (mesWoId: string | number): AxiosPromise<string> => {
  return request({
    url: `/mes/pro/work/${mesWoId}/proc`,
    method: 'get'
  });
};

/** 查询可用审批流程列表 */
export const getProcessList = (query?: any) => {
  return request({
    url: '/workflow/process/getProcesss',
    method: 'get',
    params: query
  });
};
