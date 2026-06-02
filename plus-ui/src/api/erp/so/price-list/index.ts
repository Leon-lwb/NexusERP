import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { PriceListQuery, PriceListVO, PriceListForm } from './types';

const BASE = '/erp/so/price-list';

export const listPriceList = (query: PriceListQuery): AxiosPromise<{ rows: PriceListVO[]; total: number }> =>
  request({ url: BASE + '/list', method: 'get', params: query });

export const getPriceList = (id: string | number): AxiosPromise<PriceListVO> => request({ url: BASE + '/' + id, method: 'get' });

export const addPriceList = (data: PriceListForm) => request({ url: BASE, method: 'post', data });

export const updatePriceList = (data: PriceListForm) => request({ url: BASE, method: 'put', data });

export const delPriceList = (id: string | number | (string | number)[]) => request({ url: BASE + '/' + id, method: 'delete' });

export const exportPriceList = (query: PriceListQuery) => request({ url: BASE + '/export', method: 'post', params: query });
