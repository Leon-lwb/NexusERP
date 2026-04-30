import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ShiftQuery, ShiftVO, ShiftForm } from './types';

export const listShift = (query: ShiftQuery): AxiosPromise<ShiftVO[]> => {
  return request({
    url: '/erp/sch/shift/list',
    method: 'get',
    params: query
  });
};

export const getShift = (shiftId: string | number): AxiosPromise<ShiftVO> => {
  return request({
    url: '/erp/sch/shift/' + shiftId,
    method: 'get'
  });
};

export const addShift = (data: ShiftForm) => {
  return request({
    url: '/erp/sch/shift',
    method: 'post',
    data
  });
};

export const updateShift = (data: ShiftForm) => {
  return request({
    url: '/erp/sch/shift',
    method: 'put',
    data
  });
};

export const delShift = (shiftId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/sch/shift/' + shiftId,
    method: 'delete'
  });
};
