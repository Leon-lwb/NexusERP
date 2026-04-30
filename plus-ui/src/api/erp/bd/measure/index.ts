import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { MeasureQuery, MeasureVO, MeasureForm } from './types';

export const listMeasure = (query: MeasureQuery): AxiosPromise<MeasureVO[]> => {
  return request({
    url: '/erp/bd/measure/list',
    method: 'get',
    params: query
  });
};

export const getMeasure = (uomId: string | number): AxiosPromise<MeasureVO> => {
  return request({
    url: '/erp/bd/measure/' + uomId,
    method: 'get'
  });
};

export const addMeasure = (data: MeasureForm) => {
  return request({
    url: '/erp/bd/measure',
    method: 'post',
    data
  });
};

export const updateMeasure = (data: MeasureForm) => {
  return request({
    url: '/erp/bd/measure',
    method: 'put',
    data
  });
};

export const delMeasure = (uomId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/bd/measure/' + uomId,
    method: 'delete'
  });
};

export const listMeasureAll = (query?: MeasureQuery): AxiosPromise<MeasureVO[]> => {
  return request({
    url: '/erp/bd/measure/all',
    method: 'get',
    params: query
  });
};
