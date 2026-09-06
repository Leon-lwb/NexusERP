import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { WmSnVO, WmSnQuery, WmSnForm } from './types';

export function listSn(query: WmSnQuery): AxiosPromise<WmSnVO[]> {
  return request({ url: '/erp/wm/sn/list', method: 'get', params: query });
}

export function getSn(snId: number): AxiosPromise<WmSnVO> {
  return request({ url: '/erp/wm/sn/' + snId, method: 'get' });
}

export function addSn(data: WmSnForm) {
  return request({ url: '/erp/wm/sn', method: 'post', data });
}

export function updateSn(data: WmSnForm) {
  return request({ url: '/erp/wm/sn', method: 'put', data });
}

export function delSn(snIds: string | number | (string | number)[]) {
  return request({ url: '/erp/wm/sn/' + snIds, method: 'delete' });
}

// 导出走 useCrud handleExport → download(exportUrl, tansParams), 此处不再提供 export 函数

export function batchGenerateSn(data: {
  materialId?: number;
  quantity: number;
  batchId?: number;
  ruleId?: number;
  sourceDocId?: number;
  sourceDocType?: string;
  sourceDocNo?: string;
}) {
  return request({ url: '/erp/wm/sn/batchGenerate', method: 'post', data });
}

export function bindStock(data: { snId: number; stockId?: number; warehouseId?: number; locationId?: number }) {
  return request({ url: '/erp/wm/sn/bindStock', method: 'put', data });
}

export function unbindStock(snId: number) {
  return request({ url: '/erp/wm/sn/unbindStock/' + snId, method: 'put' });
}

export function changeStatus(data: { snId: number; status: string }) {
  return request({ url: '/erp/wm/sn/changeStatus', method: 'put', data });
}
