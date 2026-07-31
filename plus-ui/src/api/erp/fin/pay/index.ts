import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 模拟支付渠道收款回调 */
export const payMockCallback = (data: {
  payOrderNumber: string;
  amount: number;
  thirdPartyNo?: string;
  clientId: number;
  payType?: string;
}): AxiosPromise<any> => {
  return request({ url: '/erp/fin/pay/mock/callback', method: 'post', data });
};

/** 未核销 AR 发票列表(客户) */
export const listOpenInvoices = (params: { clientId: number; status?: string }): AxiosPromise<any> => {
  return request({ url: '/erp/so/invoice/list', method: 'get', params: { ...params, pageNum: 1, pageSize: 100 } });
};
