import request from '@/utils/request';
import { SalesReportQuery } from './types';

export function getSalesExecution(query: SalesReportQuery) {
  return request({ url: '/erp/so/report/execution', method: 'get', params: query });
}

export function getCustomerRanking(query: SalesReportQuery) {
  return request({ url: '/erp/so/report/customerRanking', method: 'get', params: query });
}
