import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { WarehouseQuery, WarehouseVO, WarehouseForm } from './types';

export const listWarehouse = (query: WarehouseQuery): AxiosPromise<WarehouseVO[]> => {
  return request({
    url: '/erp/wm/warehouse/list',
    method: 'get',
    params: query
  });
};

export const getWarehouse = (warehouseId: string | number): AxiosPromise<WarehouseVO> => {
  return request({
    url: '/erp/wm/warehouse/' + warehouseId,
    method: 'get'
  });
};

export const getWareHouseTree = (): AxiosPromise<any> => {
  return request({
    url: '/erp/wm/warehouse/getWareHouseTree',
    method: 'get'
  });
};

export const addWarehouse = (data: WarehouseForm) => {
  return request({
    url: '/erp/wm/warehouse',
    method: 'post',
    data
  });
};

export const updateWarehouse = (data: WarehouseForm) => {
  return request({
    url: '/erp/wm/warehouse',
    method: 'put',
    data
  });
};

export const delWarehouse = (warehouseId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/wm/warehouse/' + warehouseId,
    method: 'delete'
  });
};
