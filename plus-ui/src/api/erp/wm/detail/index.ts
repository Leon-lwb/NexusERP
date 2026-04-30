import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { DetailQuery, DetailVO, DetailForm } from './types';

export const listDetail = (query: DetailQuery): AxiosPromise<DetailVO[]> => {
  return request({
    url: '/erp/wm/detail/list',
    method: 'get',
    params: query
  });
};

export const getDetail = (detailId: string | number): AxiosPromise<DetailVO> => {
  return request({
    url: '/erp/wm/detail/' + detailId,
    method: 'get'
  });
};

export const addDetail = (data: DetailForm) => {
  return request({
    url: '/erp/wm/detail',
    method: 'post',
    data
  });
};

export const updateDetail = (data: DetailForm) => {
  return request({
    url: '/erp/wm/detail',
    method: 'put',
    data
  });
};

export const delDetail = (detailId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/wm/detail/' + detailId,
    method: 'delete'
  });
};
