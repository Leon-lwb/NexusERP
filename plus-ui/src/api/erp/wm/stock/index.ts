import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { StockQuery, StockVO, StockForm } from './types';

export const listStock = (query: StockQuery): AxiosPromise<StockVO[]> => {
  return request({
    url: '/erp/wm/stock/list',
    method: 'get',
    params: query
  });
};

export const getStock = (stockId: string | number): AxiosPromise<StockVO> => {
  return request({
    url: '/erp/wm/stock/' + stockId,
    method: 'get'
  });
};

export const addStock = (data: StockForm) => {
  return request({
    url: '/erp/wm/stock',
    method: 'post',
    data
  });
};

export const updateStock = (data: StockForm) => {
  return request({
    url: '/erp/wm/stock',
    method: 'put',
    data
  });
};

export const delStock = (stockId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/wm/stock/' + stockId,
    method: 'delete'
  });
};

export const getCategoryStocks = (categoryIds: number[] | string) => {
  return request({
    url: '/erp/wm/stock/categoryStocks/' + categoryIds,
    method: 'get'
  });
};
