import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { WorkerQuery, WorkerVO, WorkerForm } from './types';

export const listWorker = (query: WorkerQuery): AxiosPromise<WorkerVO[]> => {
  return request({
    url: '/erp/sch/worker/list',
    method: 'get',
    params: query
  });
};

export const getWorker = (workerId: string | number): AxiosPromise<WorkerVO> => {
  return request({
    url: '/erp/sch/worker/' + workerId,
    method: 'get'
  });
};

export const addWorker = (data: WorkerForm) => {
  return request({
    url: '/erp/sch/worker',
    method: 'post',
    data
  });
};

export const updateWorker = (data: WorkerForm) => {
  return request({
    url: '/erp/sch/worker',
    method: 'put',
    data
  });
};

export const delWorker = (workerId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/sch/worker/' + workerId,
    method: 'delete'
  });
};
