import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { BomQuery, BomVO, BomForm, BomDetailVO, BomDetailForm, BomTreeNodeVo, IndentedBomLineVo, BomComparisonVo } from './types';

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

export const activateBomVersion = (bomId: number) =>
  request({ url: '/erp/bd/bom/activateVersion/' + bomId, method: 'put' });

export const verifyBom = (bomId: number): AxiosPromise<string> =>
  request({ url: '/erp/bd/bom/verify/' + bomId, method: 'get' });

export const getBomTree = (bomId: number): AxiosPromise<BomTreeNodeVo> => {
  return request({
    url: '/erp/bd/bom/tree/' + bomId,
    method: 'get'
  });
};

export const getIndentedBom = (bomId: number): AxiosPromise<IndentedBomLineVo[]> => {
  return request({
    url: '/erp/bd/bom/indented/' + bomId,
    method: 'get'
  });
};

export const explodeBom = (bomId: number): AxiosPromise<IndentedBomLineVo[]> => {
  return request({
    url: '/erp/bd/bom/explode/' + bomId,
    method: 'get'
  });
};

/**
 * Where-Used反查：查询某物料被哪些BOM使用
 */
export const whereUsedBom = (materialId: number): AxiosPromise<BomVO[]> => {
  return request({
    url: '/erp/bd/bom/whereUsed/' + materialId,
    method: 'get'
  });
};

/**
 * BOM复制：从已有BOM复制创建新版本
 */
export const copyBom = (sourceBomId: number, newVersion: string): AxiosPromise<number> => {
  return request({
    url: '/erp/bd/bom/copy/' + sourceBomId,
    method: 'post',
    params: { newVersion }
  });
};

/**
 * BOM比较：对比两个版本的差异
 */
export const compareBom = (bomId1: number, bomId2: number): AxiosPromise<BomComparisonVo> => {
  return request({
    url: '/erp/bd/bom/compare',
    method: 'get',
    params: { bomId1, bomId2 }
  });
};
