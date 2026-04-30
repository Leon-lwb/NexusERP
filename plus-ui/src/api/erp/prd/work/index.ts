import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { WorkQuery, WorkVO, WorkForm } from './types';

export const listWork = (query: WorkQuery): AxiosPromise<WorkVO[]> => {
  return request({
    url: '/erp/prd/work/list',
    method: 'get',
    params: query
  });
};

export const getWork = (woId: string | number): AxiosPromise<WorkVO> => {
  return request({
    url: '/erp/prd/work/' + woId,
    method: 'get'
  });
};

export const addWork = (data: WorkForm) => {
  return request({
    url: '/erp/prd/work',
    method: 'post',
    data
  });
};

export const updateWork = (data: WorkForm) => {
  return request({
    url: '/erp/prd/work',
    method: 'put',
    data
  });
};

export const delWork = (woId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/prd/work/' + woId,
    method: 'delete'
  });
};
