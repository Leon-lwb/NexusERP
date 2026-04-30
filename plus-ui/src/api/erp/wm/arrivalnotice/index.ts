import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ArrivalnoticeQuery, ArrivalnoticeVO, ArrivalnoticeForm } from './types';

export const listArrivalnotice = (query: ArrivalnoticeQuery): AxiosPromise<ArrivalnoticeVO[]> => {
  return request({
    url: '/erp/wm/arrivalnotice/list',
    method: 'get',
    params: query
  });
};

export const getArrivalnotice = (noticeId: string | number): AxiosPromise<ArrivalnoticeVO> => {
  return request({
    url: '/erp/wm/arrivalnotice/' + noticeId,
    method: 'get'
  });
};

export const addArrivalnotice = (data: ArrivalnoticeForm) => {
  return request({
    url: '/erp/wm/arrivalnotice',
    method: 'post',
    data
  });
};

export const updateArrivalnotice = (data: ArrivalnoticeForm) => {
  return request({
    url: '/erp/wm/arrivalnotice',
    method: 'put',
    data
  });
};

export const delArrivalnotice = (noticeId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/wm/arrivalnotice/' + noticeId,
    method: 'delete'
  });
};
