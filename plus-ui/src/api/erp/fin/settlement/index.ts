import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { FinSettlementQuery, FinSettlementVO, FinSettlementForm, OpenItemVO } from './types';

/** 分页查询核销记录 */
export const listFinSettlement = (query: FinSettlementQuery): AxiosPromise<FinSettlementVO[]> => {
  return request({ url: '/erp/fin/arap/settlement/list', method: 'get', params: query });
};

/** 核销:发票 <-> 收付款单 */
export const settleFinSettlement = (data: FinSettlementForm) => {
  return request({ url: '/erp/fin/arap/settlement/settle', method: 'post', data });
};

/** 查询未清项(核销选单:未清发票 + 未清收付款单) */
export const listOpenItems = (direction: string, partnerId?: string | number): AxiosPromise<OpenItemVO[]> => {
  return request({ url: '/erp/fin/arap/settlement/openItems', method: 'get', params: { direction, partnerId } });
};
