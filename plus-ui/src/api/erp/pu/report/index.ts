import request from '@/utils/request';
import { PurchaseReportQuery } from './types';

export function getPurchaseExecution(query: PurchaseReportQuery) {
  return request({ url: '/erp/pu/report/execution', method: 'get', params: query });
}

export function getVendorDeliveryRate(query: PurchaseReportQuery) {
  return request({ url: '/erp/pu/report/deliveryRate', method: 'get', params: query });
}
