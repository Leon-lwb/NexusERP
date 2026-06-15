import request from '@/utils/request';
import { ProductionReportQuery } from './types';

export function getWorkOrderCompletionRate(query: ProductionReportQuery) {
  return request({ url: '/erp/prd/report/completionRate', method: 'get', params: query });
}

export function getProductionEfficiency(query: ProductionReportQuery) {
  return request({ url: '/erp/prd/report/efficiency', method: 'get', params: query });
}
