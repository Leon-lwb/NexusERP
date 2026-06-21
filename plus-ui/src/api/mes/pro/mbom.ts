import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { MesMbomVO, MesMbomForm, MesMbomQuery } from './types';

/** 分页查询MBOM列表 */
export const listMbom = (query: MesMbomQuery): AxiosPromise<MesMbomVO[]> => {
  return request({ url: '/mes/pro/mbom/list', method: 'get', params: query });
};

/** 根据ID获取MBOM详情(含明细行) */
export const getMbom = (mbomId: number): AxiosPromise<MesMbomVO> => {
  return request({ url: '/mes/pro/mbom/' + mbomId, method: 'get' });
};

/** 根据ERP BOM ID获取MBOM */
export const getMbomByBomId = (bomId: number): AxiosPromise<MesMbomVO> => {
  return request({ url: '/mes/pro/mbom/byBom/' + bomId, method: 'get' });
};

/** 新增MBOM */
export const addMbom = (data: MesMbomForm) => {
  return request({ url: '/mes/pro/mbom', method: 'post', data });
};

/** 修改MBOM */
export const updateMbom = (data: MesMbomForm) => {
  return request({ url: '/mes/pro/mbom', method: 'put', data });
};

/** 删除MBOM */
export const delMbom = (mbomId: number | number[]) => {
  return request({ url: '/mes/pro/mbom/' + mbomId, method: 'delete' });
};

/** 激活MBOM */
export const activateMbom = (mbomId: number) => {
  return request({ url: '/mes/pro/mbom/activate/' + mbomId, method: 'put' });
};

/** 校验MBOM完整性 */
export const verifyMbom = (mbomId: number): AxiosPromise<string> => {
  return request({ url: '/mes/pro/mbom/verify/' + mbomId, method: 'get' });
};
