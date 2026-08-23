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

export const releaseWork = (woId: number) => request({ url: '/erp/prd/work/release/' + woId, method: 'put' });

export const startWork = (woId: number) => request({ url: '/erp/prd/work/start/' + woId, method: 'put' });

export const completeWork = (woId: number) => request({ url: '/erp/prd/work/complete/' + woId, method: 'put' });

export const closeWork = (woId: number) => request({ url: '/erp/prd/work/close/' + woId, method: 'put' });

export const submitWorkFlow = (woId: string | number, selectFlowId?: string) =>
  request({ url: '/erp/prd/work/' + woId + '/submit', method: 'post', params: { selectFlowId } });

export const getWorkProcId = (woId: string | number) => request({ url: '/erp/prd/work/' + woId + '/proc', method: 'get' });
