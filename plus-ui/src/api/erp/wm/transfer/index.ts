import { traceSave, executeTraceDocument } from '@/api/erp/wm/identity/document';
import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { TransferQuery, TransferVO, TransferForm } from './types';

const BASE = '/erp/wm/transfer';

export const listTransfer = (query: TransferQuery): AxiosPromise<TransferVO[]> => request({ url: BASE + '/list', method: 'get', params: query });

export const getTransfer = (id: string | number): AxiosPromise<TransferVO> => request({ url: BASE + '/' + id, method: 'get' });

export const addTransfer = (data: TransferForm) => request({ url: BASE, method: 'post', data: traceSave(data) });

export const updateTransfer = (data: TransferForm) => request({ url: BASE, method: 'put', data: traceSave(data) });

export const delTransfer = (id: string | number | (string | number)[]) => request({ url: BASE + '/' + id, method: 'delete' });

export const exportTransfer = (query: TransferQuery) => request({ url: BASE + '/export', method: 'post', params: query });

export const executeTransfer = (id: string | number, data?: Record<string, any>) =>
  executeTraceDocument({ url: BASE + '/execute/' + id, detailUrl: BASE + '/' + id, linesKey: 'wmTransferLineList', quantityKey: 'quantity', data });

// ==================== Flowable 审批 ====================

export const submitTransferFlow = (id: string | number, selectFlowId?: string) => {
  return request({
    url: BASE + '/' + id + '/submit',
    method: 'post',
    params: { selectFlowId }
  });
};

export const getTransferProcId = (id: string | number) => {
  return request({
    url: BASE + '/' + id + '/proc',
    method: 'get'
  });
};
