import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { StockTakingPlanQuery, StockTakingPlanVO, StockTakingPlanForm } from './types';

const BASE = '/erp/wm/stocktaking';

export const listStockTakingPlan = (query: StockTakingPlanQuery): AxiosPromise<{ rows: StockTakingPlanVO[]; total: number }> =>
  request({ url: BASE + '/list', method: 'get', params: query });

export const getStockTakingPlan = (id: string | number): AxiosPromise<StockTakingPlanVO> =>
  request({ url: BASE + '/' + id, method: 'get' });

export const addStockTakingPlan = (data: StockTakingPlanForm) =>
  request({ url: BASE, method: 'post', data });

export const updateStockTakingPlan = (data: StockTakingPlanForm) =>
  request({ url: BASE, method: 'put', data });

export const delStockTakingPlan = (id: string | number | (string | number)[]) =>
  request({ url: BASE + '/' + id, method: 'delete' });

export const exportStockTakingPlan = (query: StockTakingPlanQuery) =>
  request({ url: BASE + '/export', method: 'post', params: query });

export const generateLines = (planId: number) =>
  request({ url: BASE + '/generateLines/' + planId, method: 'put' });

export const postAdjustment = (planId: number) =>
  request({ url: BASE + '/postAdjustment/' + planId, method: 'put' });
