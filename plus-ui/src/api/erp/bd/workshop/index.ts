import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { WorkshopQuery, WorkshopVO, WorkshopForm } from './types';

export const listWorkshop = (query: WorkshopQuery): AxiosPromise<WorkshopVO[]> => {
  return request({
    url: '/erp/bd/workshop/list',
    method: 'get',
    params: query
  });
};

export const getWorkshop = (workshopId: string | number): AxiosPromise<WorkshopVO> => {
  return request({
    url: '/erp/bd/workshop/' + workshopId,
    method: 'get'
  });
};

export const addWorkshop = (data: WorkshopForm) => {
  return request({
    url: '/erp/bd/workshop',
    method: 'post',
    data
  });
};

export const updateWorkshop = (data: WorkshopForm) => {
  return request({
    url: '/erp/bd/workshop',
    method: 'put',
    data
  });
};

export const delWorkshop = (workshopId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/bd/workshop/' + workshopId,
    method: 'delete'
  });
};
