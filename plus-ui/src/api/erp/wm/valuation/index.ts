import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ValuationQuery, ValuationVO } from './types';

export const listValuation = (query: ValuationQuery): AxiosPromise<ValuationVO[]> => {
  return request({
    url: '/erp/wm/valuation/list',
    method: 'get',
    params: query
  });
};

export const exportValuation = (query: ValuationQuery) => {
  // 后端 export(WmMaterialStockBo) 无 @RequestBody, 走表单/URL参数绑定, 必须传 params
  return request({
    url: '/erp/wm/valuation/export',
    method: 'post',
    params: query,
    responseType: 'blob'
  });
};
