import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** SN批次绑定视图对象 */
export interface LtSnBindingVO {
  bindingId: number;
  snId: number;
  snCode?: string;
  lotId: number;
  lotCode?: string;
  materialId: number;
  materialCode?: string;
  materialName?: string;
  boundTime: string;
  boundBy?: number;
  boundByName?: string;
}

/** SN批次绑定查询条件 */
export interface LtSnBindingQuery extends PageQuery {
  snId?: number;
  lotId?: number;
  materialId?: number;
}

/** SN批次绑定表单 */
export interface LtSnBindingForm {
  bindingId?: number;
  snId: number;
  snCode?: string;
  lotId: number;
  lotCode?: string;
  materialId: number;
  materialCode?: string;
  materialName?: string;
}

/** 分页查询SN批次绑定列表 */
export const listBinding = (query?: LtSnBindingQuery): AxiosPromise<LtSnBindingVO[]> =>
  request({ url: '/mes/lt/binding/list', method: 'get', params: query });

/** 获取绑定详情 */
export const getBinding = (bindingId: number | string): AxiosPromise<LtSnBindingVO> =>
  request({ url: `/mes/lt/binding/${bindingId}`, method: 'get' });

/** 新增SN-批次绑定 */
export const addBinding = (data: LtSnBindingForm) => request({ url: '/mes/lt/binding', method: 'post', data });

/** 批量删除绑定 */
export const delBinding = (bindingIds: number | number[]) => request({ url: `/mes/lt/binding/${bindingIds}`, method: 'delete' });

/** 按SN ID查询其批次绑定 */
export const listBindingBySn = (snId: number | string): AxiosPromise<LtSnBindingVO[]> =>
  request({ url: `/mes/lt/binding/by-sn/${snId}`, method: 'get' });

/** 按批次ID查询所有SN绑定 */
export const listBindingByLot = (lotId: number | string): AxiosPromise<LtSnBindingVO[]> =>
  request({ url: `/mes/lt/binding/by-lot/${lotId}`, method: 'get' });
