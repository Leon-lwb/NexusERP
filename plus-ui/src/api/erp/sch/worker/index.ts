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

/**
 * 查询已建档工人档案的系统用户ID集合
 * 用于新建工人时用户选择弹窗排除已建档用户
 */
export const listBuiltUserIds = (): AxiosPromise<number[]> => {
  return request({
    url: '/erp/sch/worker/built-user-ids',
    method: 'get'
  });
};
