import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { WorkstationQuery, WorkstationVO, WorkstationForm } from './types';

export const listWorkstation = (query: WorkstationQuery): AxiosPromise<WorkstationVO[]> => {
  return request({
    url: '/erp/bd/workstation/list',
    method: 'get',
    params: query
  });
};

export const getWorkstation = (workstationId: string | number): AxiosPromise<WorkstationVO> => {
  return request({
    url: '/erp/bd/workstation/' + workstationId,
    method: 'get'
  });
};

export const addWorkstation = (data: WorkstationForm) => {
  return request({
    url: '/erp/bd/workstation',
    method: 'post',
    data
  });
};

export const updateWorkstation = (data: WorkstationForm) => {
  return request({
    url: '/erp/bd/workstation',
    method: 'put',
    data
  });
};

export const delWorkstation = (workstationId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/bd/workstation/' + workstationId,
    method: 'delete'
  });
};
