import { traceSave, executeTraceDocument } from '@/api/erp/wm/identity/document';
import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { StockTakingPlanQuery, StockTakingPlanVO, StockTakingPlanForm } from './types';

const BASE = '/erp/wm/stocktaking';

export const listStockTakingPlan = (query: StockTakingPlanQuery): AxiosPromise<StockTakingPlanVO[]> =>
  request({ url: BASE + '/list', method: 'get', params: query });

export const getStockTakingPlan = (id: string | number): AxiosPromise<StockTakingPlanVO> => request({ url: BASE + '/' + id, method: 'get' });

export const addStockTakingPlan = (data: StockTakingPlanForm) => request({ url: BASE, method: 'post', data: traceSave(data) });

export const updateStockTakingPlan = (data: StockTakingPlanForm) => request({ url: BASE, method: 'put', data: traceSave(data) });

export const delStockTakingPlan = (id: string | number | (string | number)[]) => request({ url: BASE + '/' + id, method: 'delete' });

export const exportStockTakingPlan = (query: StockTakingPlanQuery) => request({ url: BASE + '/export', method: 'post', params: query });

export const generateLines = (planId: number) => request({ url: BASE + '/generateLines/' + planId, method: 'put' });

export const postAdjustment = (planId: string | number, data?: Record<string, any>) =>
  executeTraceDocument({
    url: BASE + '/postAdjustment/' + planId,
    detailUrl: BASE + '/' + planId,
    linesKey: 'wmStockTakingLineList',
    quantityKey: 'differenceQuantity',
    data
  });

/**
 * 提交流程审批（Flowable 审批流）
 * @param id 盘点计划ID
 * @param selectFlowId 审批流ID
 * @returns 请求结果
 */
export const submitStockTakingFlow = (id: string, selectFlowId?: string) =>
  request({
    url: BASE + '/submit/' + id,
    method: 'post',
    params: selectFlowId ? { selectFlowId } : {}
  });

/**
 * 获取流程实例ID
 * @param id 盘点计划ID
 * @returns 流程实例ID
 */
export const getStockTakingProcId = (id: string) =>
  request({
    url: BASE + '/proc/' + id,
    method: 'get'
  });
