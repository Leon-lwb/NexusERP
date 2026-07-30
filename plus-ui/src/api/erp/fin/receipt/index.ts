import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { FinReceiptQuery, FinReceiptVO, FinReceiptForm } from './types';

/** 分页查询收付款单 */
export const listFinReceipt = (query: FinReceiptQuery): AxiosPromise<FinReceiptVO[]> => {
  return request({ url: '/erp/fin/arap/receipt/list', method: 'get', params: query });
};

/** 收付款单详情 */
export const getFinReceipt = (receiptId: string | number): AxiosPromise<FinReceiptVO> => {
  return request({ url: '/erp/fin/arap/receipt/' + receiptId, method: 'get' });
};

/** 新增收付款单 */
export const addFinReceipt = (data: FinReceiptForm) => {
  return request({ url: '/erp/fin/arap/receipt', method: 'post', data });
};

/** 修改收付款单 */
export const updateFinReceipt = (data: FinReceiptForm) => {
  return request({ url: '/erp/fin/arap/receipt', method: 'put', data });
};

/** 删除收付款单 */
export const delFinReceipt = (receiptId: string | number | (string | number)[]) => {
  return request({ url: '/erp/fin/arap/receipt/' + receiptId, method: 'delete' });
};

/** 确认收付款单(DRAFT->CONFIRMED,触发 GL 过账) */
export const confirmFinReceipt = (receiptId: string | number) => {
  return request({ url: '/erp/fin/arap/receipt/confirm/' + receiptId, method: 'put' });
};

/** 作废收付款单(仅 DRAFT) */
export const cancelFinReceipt = (receiptId: string | number) => {
  return request({ url: '/erp/fin/arap/receipt/cancel/' + receiptId, method: 'put' });
};
