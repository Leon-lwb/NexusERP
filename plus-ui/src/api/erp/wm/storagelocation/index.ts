import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { StoragelocationQuery, StoragelocationVO, StoragelocationForm } from './types';

export const listStoragelocation = (query: StoragelocationQuery): AxiosPromise<StoragelocationVO[]> => {
  return request({
    url: '/erp/wm/storagelocation/list',
    method: 'get',
    params: query
  });
};

export const getStoragelocation = (locationId: string | number): AxiosPromise<StoragelocationVO> => {
  return request({
    url: '/erp/wm/storagelocation/' + locationId,
    method: 'get'
  });
};

export const addStoragelocation = (data: StoragelocationForm) => {
  return request({
    url: '/erp/wm/storagelocation',
    method: 'post',
    data
  });
};

export const updateStoragelocation = (data: StoragelocationForm) => {
  return request({
    url: '/erp/wm/storagelocation',
    method: 'put',
    data
  });
};

export const delStoragelocation = (locationId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/wm/storagelocation/' + locationId,
    method: 'delete'
  });
};
