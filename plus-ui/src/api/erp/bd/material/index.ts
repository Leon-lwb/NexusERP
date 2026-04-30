import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { MaterialQuery, MaterialVO, MaterialForm } from './types';

export const listMaterial = (query: MaterialQuery): AxiosPromise<MaterialVO[]> => {
  return request({
    url: '/erp/bd/material/list',
    method: 'get',
    params: query
  });
};

export const getMaterial = (materialId: string | number): AxiosPromise<MaterialVO> => {
  return request({
    url: '/erp/bd/material/' + materialId,
    method: 'get'
  });
};

export const addMaterial = (data: MaterialForm) => {
  return request({
    url: '/erp/bd/material',
    method: 'post',
    data
  });
};

export const updateMaterial = (data: MaterialForm) => {
  return request({
    url: '/erp/bd/material',
    method: 'put',
    data
  });
};

export const delMaterial = (materialId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/bd/material/' + materialId,
    method: 'delete'
  });
};
