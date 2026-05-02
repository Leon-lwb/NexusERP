import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { PayInvoiceQuery, PayInvoiceVO, PayInvoiceForm } from './types';

export const listPayInvoice = (query: PayInvoiceQuery): AxiosPromise<PayInvoiceVO[]> => {
  return request({
    url: '/pay/invoice/list',
    method: 'get',
    params: query
  });
};

export const getPayInvoice = (invoiceId: string | number): AxiosPromise<PayInvoiceVO> => {
  return request({
    url: '/pay/invoice/' + invoiceId,
    method: 'get'
  });
};

export const addPayInvoice = (data: PayInvoiceForm) => {
  return request({
    url: '/pay/invoice',
    method: 'post',
    data
  });
};

export const updatePayInvoice = (data: PayInvoiceForm) => {
  return request({
    url: '/pay/invoice',
    method: 'put',
    data
  });
};

export const delPayInvoice = (invoiceId: string | number | (string | number)[]) => {
  return request({
    url: '/pay/invoice/' + invoiceId,
    method: 'delete'
  });
};
