import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ReturnQuery, ReturnVO, ReturnForm } from './types';

const BASE = '/erp/so/return';

export const listReturn = (query: ReturnQuery): AxiosPromise<{ rows: ReturnVO[]; total: number }> =>
  request({ url: BASE + '/list', method: 'get', params: query });

export const getReturn = (id: string | number): AxiosPromise<ReturnVO> =>
  request({ url: BASE + '/' + id, method: 'get' });

export const addReturn = (data: ReturnForm) =>
  request({ url: BASE, method: 'post', data });

export const updateReturn = (data: ReturnForm) =>
  request({ url: BASE, method: 'put', data });

export const delReturn = (id: string | number | (string | number)[]) =>
  request({ url: BASE + '/' + id, method: 'delete' });

export const exportReturn = (query: ReturnQuery) =>
  request({ url: BASE + '/export', method: 'post', params: query });

export const confirmReturn = (id: number) =>
  request({ url: BASE + '/confirm/' + id, method: 'put' });

export const executeReturn = (id: number) =>
  request({ url: BASE + '/execute/' + id, method: 'put' });
