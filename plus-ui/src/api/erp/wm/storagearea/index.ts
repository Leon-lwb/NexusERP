import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { StorageareaQuery, StorageareaVO, StorageareaForm } from './types';

export const listStoragearea = (query: StorageareaQuery): AxiosPromise<StorageareaVO[]> => {
  return request({
    url: '/erp/wm/storagearea/list',
    method: 'get',
    params: query
  });
};

export const getStoragearea = (areaId: string | number): AxiosPromise<StorageareaVO> => {
  return request({
    url: '/erp/wm/storagearea/' + areaId,
    method: 'get'
  });
};

export const addStoragearea = (data: StorageareaForm) => {
  return request({
    url: '/erp/wm/storagearea',
    method: 'post',
    data
  });
};

export const updateStoragearea = (data: StorageareaForm) => {
  return request({
    url: '/erp/wm/storagearea',
    method: 'put',
    data
  });
};

export const delStoragearea = (areaId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/wm/storagearea/' + areaId,
    method: 'delete'
  });
};
