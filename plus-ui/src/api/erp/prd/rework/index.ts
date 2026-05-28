import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { PrdReworkOrderVO, PrdReworkOrderQuery, PrdReworkOrderForm } from './types';

export function listReworkOrder(query: PrdReworkOrderQuery): AxiosPromise<PrdReworkOrderVO[]> {
  return request({ url: '/erp/prd/rework/list', method: 'get', params: query });
}

export function getReworkOrder(reworkId: number): AxiosPromise<PrdReworkOrderVO> {
  return request({ url: '/erp/prd/rework/' + reworkId, method: 'get' });
}

export function addReworkOrder(data: PrdReworkOrderForm) {
  return request({ url: '/erp/prd/rework', method: 'post', data });
}

export function updateReworkOrder(data: PrdReworkOrderForm) {
  return request({ url: '/erp/prd/rework', method: 'put', data });
}

export function delReworkOrder(reworkIds: number | number[]) {
  return request({ url: '/erp/prd/rework/' + reworkIds, method: 'delete' });
}

export function startReworkOrder(reworkId: number) {
  return request({ url: '/erp/prd/rework/start/' + reworkId, method: 'put' });
}

export function completeReworkOrder(reworkId: number) {
  return request({ url: '/erp/prd/rework/complete/' + reworkId, method: 'put' });
}
