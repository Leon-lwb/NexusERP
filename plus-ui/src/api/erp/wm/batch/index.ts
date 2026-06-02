import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { WmBatchVO, WmBatchQuery, WmBatchForm } from './types';

export function listBatch(query: WmBatchQuery): AxiosPromise<WmBatchVO[]> {
  return request({ url: '/erp/wm/batch/list', method: 'get', params: query });
}

export function getBatch(batchId: number): AxiosPromise<WmBatchVO> {
  return request({ url: '/erp/wm/batch/' + batchId, method: 'get' });
}

export function addBatch(data: WmBatchForm) {
  return request({ url: '/erp/wm/batch', method: 'post', data });
}

export function updateBatch(data: WmBatchForm) {
  return request({ url: '/erp/wm/batch', method: 'put', data });
}

export function delBatch(batchIds: number | number[]) {
  return request({ url: '/erp/wm/batch/' + batchIds, method: 'delete' });
}

export function exportBatch(data: WmBatchQuery) {
  return request({ url: '/erp/wm/batch/export', method: 'post', data, responseType: 'blob' });
}

export function createBatch(data: any) {
  return request({ url: '/erp/wm/batch/createBatch', method: 'post', data });
}

export function updateQuantity(batchId: number, deltaQty: number, isReceive: boolean) {
  return request({ url: '/erp/wm/batch/updateQuantity', method: 'put', params: { batchId, deltaQty, isReceive } });
}

export function changeStatus(batchId: number, status: string) {
  return request({ url: '/erp/wm/batch/changeStatus', method: 'put', params: { batchId, status } });
}

export function checkExpiry() {
  return request({ url: '/erp/wm/batch/checkExpiry', method: 'post' });
}
