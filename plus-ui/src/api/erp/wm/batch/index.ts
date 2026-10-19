import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { WmBatchVO, WmBatchQuery, WmBatchForm } from './types';

export function listBatch(query: WmBatchQuery): AxiosPromise<WmBatchVO[]> {
  return request({ url: '/erp/wm/batch/list', method: 'get', params: query });
}

export function getBatch(batchId: string | number): AxiosPromise<WmBatchVO> {
  return request({ url: '/erp/wm/batch/' + batchId, method: 'get' });
}

export function addBatch(data: WmBatchForm) {
  return request({ url: '/erp/wm/batch', method: 'post', data });
}

/** 批次主体、原来源仓库和累计过账数字不可修改，仅更新元数据。 */
export async function updateBatch(data: WmBatchForm, original?: WmBatchVO) {
  if (!data.batchId) throw new Error('缺少原批次身份');
  const snapshot = original || (await getBatch(data.batchId)).data;
  if (snapshot.batchId !== data.batchId) throw new Error('原批次身份不一致');
  const immutable = [
    'batchId',
    'batchCode',
    'materialId',
    'materialCode',
    'materialName',
    'materialSpec',
    'vendorId',
    'vendorCode',
    'vendorName',
    'warehouseId',
    'warehouseCode',
    'warehouseName',
    'productionDate',
    'quantity',
    'receivedQty',
    'issuedQty',
    'sourceDocType',
    'sourceDocId',
    'sourceDocNo',
    'ruleId'
  ] as const;
  for (const key of immutable)
    if (Object.prototype.hasOwnProperty.call(data, key) && data[key] !== snapshot[key]) throw new Error('批次主体、来源与累计过账事实不可修改');
  const value = { ...snapshot };
  for (const key of ['status', 'expiryDate', 'remark'] as const) if (Object.prototype.hasOwnProperty.call(data, key)) value[key] = data[key];
  return request({ url: '/erp/wm/batch', method: 'put', data: value });
}

export function delBatch(batchIds: string | string[]) {
  return request({ url: '/erp/wm/batch/' + batchIds, method: 'delete' });
}

// 导出走 useCrud handleExport → download(exportUrl, tansParams), 此处不再提供 export 函数

export function createBatch(data: any) {
  return request({ url: '/erp/wm/batch/createBatch', method: 'post', data });
}

export function updateQuantity(batchId: string, deltaQty: string, isReceive: boolean) {
  return request({ url: '/erp/wm/batch/updateQuantity', method: 'put', params: { batchId, deltaQty, isReceive } });
}

export function changeStatus(batchId: string, status: string) {
  return request({ url: '/erp/wm/batch/changeStatus', method: 'put', params: { batchId, status } });
}

export function checkExpiry() {
  return request({ url: '/erp/wm/batch/checkExpiry', method: 'post' });
}
