import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { VoucherQuery, VoucherVO, VoucherForm } from './types';

/** 分页查询记账凭证 */
export const listVoucher = (query: VoucherQuery): AxiosPromise<VoucherVO[]> => {
  return request({ url: '/erp/fin/gl/voucher/list', method: 'get', params: query });
};

/** 凭证详情(含分录行) */
export const getVoucher = (voucherId: string | number): AxiosPromise<VoucherVO> => {
  return request({ url: '/erp/fin/gl/voucher/' + voucherId, method: 'get' });
};

/** 新增凭证(草稿) */
export const addVoucher = (data: VoucherForm) => {
  return request({ url: '/erp/fin/gl/voucher', method: 'post', data });
};

/** 修改凭证(仅草稿) */
export const updateVoucher = (data: VoucherForm) => {
  return request({ url: '/erp/fin/gl/voucher', method: 'put', data });
};

/** 删除凭证(仅草稿) */
export const delVoucher = (voucherId: string | number | (string | number)[]) => {
  return request({ url: '/erp/fin/gl/voucher/' + voucherId, method: 'delete' });
};

/** 过账 */
export const postVoucher = (voucherId: string | number) => {
  return request({ url: '/erp/fin/gl/voucher/post/' + voucherId, method: 'put' });
};

/** 反过账 */
export const unpostVoucher = (voucherId: string | number) => {
  return request({ url: '/erp/fin/gl/voucher/unpost/' + voucherId, method: 'put' });
};

/** 作废 */
export const cancelVoucher = (voucherId: string | number) => {
  return request({ url: '/erp/fin/gl/voucher/cancel/' + voucherId, method: 'put' });
};
