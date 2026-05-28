import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { WmReconciliationVO, WmReconciliationQuery } from './types';

export function listReconciliation(query: WmReconciliationQuery): AxiosPromise<WmReconciliationVO[]> {
  return request({ url: '/erp/wm/reconciliation/list', method: 'get', params: query });
}

export function getReconciliation(reconId: number): AxiosPromise<WmReconciliationVO> {
  return request({ url: '/erp/wm/reconciliation/' + reconId, method: 'get' });
}

export function delReconciliation(reconIds: number | number[]) {
  return request({ url: '/erp/wm/reconciliation/' + reconIds, method: 'delete' });
}

export function reconcile() {
  return request({ url: '/erp/wm/reconciliation/reconcile', method: 'post' });
}

export function reconcileByMaterial(materialId: number) {
  return request({ url: '/erp/wm/reconciliation/reconcile/' + materialId, method: 'post' });
}
