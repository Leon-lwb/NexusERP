import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ProductionRecptVO, ProductionRecptQuery, ProductionRecptForm } from './types';

export const listProductionRecpt = (query: ProductionRecptQuery): AxiosPromise<ProductionRecptVO[]> => {
  return request({
    url: '/erp/wm/productionrecpt/list',
    method: 'get',
    params: query
  });
};

export const getProductionRecpt = (recptId: string | number): AxiosPromise<ProductionRecptVO> => {
  return request({
    url: '/erp/wm/productionrecpt/' + recptId,
    method: 'get'
  });
};

export const addProductionRecpt = (data: ProductionRecptForm) => {
  return request({
    url: '/erp/wm/productionrecpt',
    method: 'post',
    data
  });
};

export const updateProductionRecpt = (data: ProductionRecptForm) => {
  return request({
    url: '/erp/wm/productionrecpt',
    method: 'put',
    data
  });
};

export const delProductionRecpt = (recptIds: string | number | (string | number)[]) => {
  return request({
    url: '/erp/wm/productionrecpt/' + recptIds,
    method: 'delete'
  });
};
