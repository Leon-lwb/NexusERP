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
  return request({
    url: '/erp/wm/valuation/export',
    method: 'post',
    data: query,
    responseType: 'blob'
  });
};
