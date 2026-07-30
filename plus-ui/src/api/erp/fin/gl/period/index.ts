import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { PeriodQuery, PeriodVO, PeriodForm } from './types';

/** 分页查询会计期间 */
export const listPeriod = (query: PeriodQuery): AxiosPromise<PeriodVO[]> => {
  return request({ url: '/erp/fin/gl/period/list', method: 'get', params: query });
};

/** 期间详情 */
export const getPeriod = (periodId: string | number): AxiosPromise<PeriodVO> => {
  return request({ url: '/erp/fin/gl/period/' + periodId, method: 'get' });
};

/** 关账 */
export const closePeriod = (periodId: string | number) => {
  return request({ url: '/erp/fin/gl/period/close/' + periodId, method: 'put' });
};

/** 锁定 */
export const lockPeriod = (periodId: string | number) => {
  return request({ url: '/erp/fin/gl/period/lock/' + periodId, method: 'put' });
};

/** 反结账 */
export const reopenPeriod = (periodId: string | number) => {
  return request({ url: '/erp/fin/gl/period/reopen/' + periodId, method: 'put' });
};

/** 生成年度期间 */
export const generateYearPeriods = (fiscalYear: number) => {
  return request({ url: '/erp/fin/gl/period/generateYear/' + fiscalYear, method: 'post' });
};
