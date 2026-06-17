import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { BomAlternativeVO, BomAlternativeForm } from './types';

/**
 * 查询某BOM明细行的替代料列表
 */
export const listAlternativeByDetail = (detailId: number): AxiosPromise<BomAlternativeVO[]> => {
  return request({
    url: '/erp/bd/bom/alternative/list/' + detailId,
    method: 'get'
  });
};

/**
 * 查询某BOM的所有替代料
 */
export const listAlternativeByBom = (bomId: number): AxiosPromise<BomAlternativeVO[]> => {
  return request({
    url: '/erp/bd/bom/alternative/bom/' + bomId,
    method: 'get'
  });
};

/**
 * 批量保存替代料（先删后插）
 */
export const saveAlternativeBatch = (detailId: number, bomId: number, data: BomAlternativeForm[]) => {
  return request({
    url: '/erp/bd/bom/alternative/batch',
    method: 'post',
    params: { detailId, bomId },
    data
  });
};

/**
 * 删除替代料
 */
export const delAlternative = (alternativeIds: string | number | (string | number)[]) => {
  return request({
    url: '/erp/bd/bom/alternative/' + alternativeIds,
    method: 'delete'
  });
};

/**
 * 获取可用替代料（考虑生效/失效日期）
 */
export const getAvailableAlternative = (detailId: number): AxiosPromise<BomAlternativeVO[]> => {
  return request({
    url: '/erp/bd/bom/alternative/available/' + detailId,
    method: 'get'
  });
};
