import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { QuotationQuery, QuotationVO, QuotationForm } from './types';

const BASE = '/erp/so/quotation';

export const listQuotation = (query: QuotationQuery): AxiosPromise<{ rows: QuotationVO[]; total: number }> =>
  request({ url: BASE + '/list', method: 'get', params: query });

export const getQuotation = (id: string | number): AxiosPromise<QuotationVO> => request({ url: BASE + '/' + id, method: 'get' });

export const addQuotation = (data: QuotationForm) => request({ url: BASE, method: 'post', data });

export const updateQuotation = (data: QuotationForm) => request({ url: BASE, method: 'put', data });

export const delQuotation = (id: string | number | (string | number)[]) => request({ url: BASE + '/' + id, method: 'delete' });

export const exportQuotation = (query: QuotationQuery) => request({ url: BASE + '/export', method: 'post', params: query });

export const confirmQuotation = (id: number) => request({ url: BASE + '/confirm/' + id, method: 'put' });

export const convertQuotationToOrder = (id: number): AxiosPromise<number> => request({ url: BASE + '/convertToOrder/' + id, method: 'post' });
