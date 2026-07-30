import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { CurrencyRateQuery, CurrencyRateVO, CurrencyRateForm } from './types';

/** 分页查询币种汇率 */
export const listCurrencyRate = (query: CurrencyRateQuery): AxiosPromise<CurrencyRateVO[]> => {
  return request({ url: '/erp/fin/gl/currency/list', method: 'get', params: query });
};

/** 汇率详情 */
export const getCurrencyRate = (rateId: string | number): AxiosPromise<CurrencyRateVO> => {
  return request({ url: '/erp/fin/gl/currency/' + rateId, method: 'get' });
};

/** 新增汇率 */
export const addCurrencyRate = (data: CurrencyRateForm) => {
  return request({ url: '/erp/fin/gl/currency', method: 'post', data });
};

/** 修改汇率 */
export const updateCurrencyRate = (data: CurrencyRateForm) => {
  return request({ url: '/erp/fin/gl/currency', method: 'put', data });
};

/** 删除汇率 */
export const delCurrencyRate = (rateId: string | number | (string | number)[]) => {
  return request({ url: '/erp/fin/gl/currency/' + rateId, method: 'delete' });
};
