import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { BomQuery, BomVO, BomForm, BomDetailVO, BomDetailForm } from './types';

export const listBom = (query: BomQuery): AxiosPromise<BomVO[]> => {
  return request({
    url: '/erp/bd/bom/list',
    method: 'get',
    params: query
  });
};

export const getBom = (bomId: string | number): AxiosPromise<BomVO> => {
  return request({
    url: '/erp/bd/bom/' + bomId,
    method: 'get'
  });
};

export const addBom = (data: BomForm) => {
  return request({
    url: '/erp/bd/bom',
    method: 'post',
    data
  });
};

export const updateBom = (data: BomForm) => {
  return request({
    url: '/erp/bd/bom',
    method: 'put',
    data
  });
};

export const delBom = (bomId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/bd/bom/' + bomId,
    method: 'delete'
  });
};

export const listBomDetails = (bomId: number): AxiosPromise<BomDetailVO[]> => {
  return request({
    url: '/erp/bd/bom/detail/list',
    method: 'get',
    params: { bomId }
  });
};

export const getBomDetail = (detailId: string | number): AxiosPromise<BomDetailVO> => {
  return request({
    url: '/erp/bd/bom/detail/' + detailId,
    method: 'get'
  });
};

export const addBomDetail = (data: BomDetailForm) => {
  return request({
    url: '/erp/bd/bom/detail',
    method: 'post',
    data
  });
};

export const updateBomDetail = (detailId: number, data: BomDetailForm) => {
  return request({
    url: '/erp/bd/bom/detail/' + detailId,
    method: 'put',
    data
  });
};

export const delBomDetail = (detailIds: string | number | (string | number)[]) => {
  return request({
    url: '/erp/bd/bom/detail/' + detailIds,
    method: 'delete'
  });
};

export const saveBomDetailsBatch = (data: BomDetailForm[]) => {
  return request({
    url: '/erp/bd/bom/detail/batch',
    method: 'post',
    data
  });
};

export const getBomStructure = (parentId: number): AxiosPromise<any> => {
  return request({
    url: '/erp/bd/bom/structure/' + parentId,
    method: 'get'
  });
};

export const getCurrentBom = (parentId: number): AxiosPromise<BomVO> => {
  return request({
    url: '/erp/bd/bom/current/' + parentId,
    method: 'get'
  });
};
