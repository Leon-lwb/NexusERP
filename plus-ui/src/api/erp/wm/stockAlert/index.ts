import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { WmStockAlertVO, WmStockAlertQuery, WmStockAlertForm } from './types';

export function listStockAlert(query: WmStockAlertQuery): AxiosPromise<WmStockAlertVO[]> {
  return request({ url: '/erp/wm/stock-alert/list', method: 'get', params: query });
}

export function getStockAlert(alertId: number): AxiosPromise<WmStockAlertVO> {
  return request({ url: '/erp/wm/stock-alert/' + alertId, method: 'get' });
}

export function updateStockAlert(data: WmStockAlertForm) {
  return request({ url: '/erp/wm/stock-alert', method: 'put', data });
}

export function delStockAlert(alertIds: number | number[]) {
  return request({ url: '/erp/wm/stock-alert/' + alertIds, method: 'delete' });
}

export function checkStockAlert() {
  return request({ url: '/erp/wm/stock-alert/check', method: 'post' });
}

export function handleStockAlert(alertId: number, handledRemark?: string) {
  return request({ url: '/erp/wm/stock-alert/handle/' + alertId, method: 'post', params: { handledRemark } });
}
