import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ProcessQuery, ProcessVO, ProcessForm } from './types';

/**
 * 查询工序列表
 */
export const listProcess = (query: ProcessQuery): AxiosPromise<ProcessVO[]> => {
  return request({
    url: '/erp/bd/process/list',
    method: 'get',
    params: query
  });
};

/**
 * 查询工序详细
 */
export const getProcess = (processId: string | number): AxiosPromise<ProcessVO> => {
  return request({
    url: '/erp/bd/process/' + processId,
    method: 'get'
  });
};

/**
 * 新增工序
 */
export const addProcess = (data: ProcessForm) => {
  return request({
    url: '/erp/bd/process',
    method: 'post',
    data
  });
};

/**
 * 修改工序
 */
export const updateProcess = (data: ProcessForm) => {
  return request({
    url: '/erp/bd/process',
    method: 'put',
    data
  });
};

/**
 * 删除工序
 */
export const delProcess = (processId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/bd/process/' + processId,
    method: 'delete'
  });
};
