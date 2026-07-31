import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 按库存流水过账 */
export const postCostTx = (txId: number): AxiosPromise<any> => {
  return request({ url: `/erp/fin/cost/postTx/${txId}`, method: 'post' });
};

/** 期间采购暂估 */
export const costProvision = (periodName: string): AxiosPromise<any> => {
  return request({ url: `/erp/fin/cost/provision/${periodName}`, method: 'post' });
};

/** 库存流水分页 */
export const listStockTx = (params: any): AxiosPromise<any> => {
  return request({ url: '/erp/wm/stocktx/list', method: 'get', params });
};
