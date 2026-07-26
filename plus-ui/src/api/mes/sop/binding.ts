import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 工序SOP绑定视图对象 */
export interface SopProcessBindingVO {
  bindingId: number;
  processId: number;
  processCode?: string;
  processName?: string;
  processCategory?: string;
  sopId: number;
  sopCode?: string;
  sopName?: string;
  sopStatus?: string;
  versionNo?: string;
  isDefault: string;
  sortOrder?: number;
  remark?: string;
  createTime?: string;
}

/** 工序SOP绑定查询条件 */
export interface SopProcessBindingQuery extends PageQuery {
  processId?: number;
  sopId?: number;
  processName?: string;
  isDefault?: string;
}

/** 工序SOP绑定表单 */
export interface SopProcessBindingForm {
  bindingId?: number;
  processId: number;
  processCode?: string;
  processName?: string;
  processCategory?: string;
  sopId: number;
  sopName?: string;
  versionNo?: string;
  isDefault?: string;
  sortOrder?: number;
  remark?: string;
}

/** 分页查询绑定列表 */
export const listBinding = (query?: SopProcessBindingQuery): AxiosPromise<SopProcessBindingVO[]> =>
  request({ url: '/mes/sop/binding/list', method: 'get', params: query });

/** 获取绑定详情 */
export const getBinding = (bindingId: number | string): AxiosPromise<SopProcessBindingVO> =>
  request({ url: `/mes/sop/binding/${bindingId}`, method: 'get' });

/** 新增绑定(is_default=1 时同工序排他置0) */
export const addBinding = (data: SopProcessBindingForm) => request({ url: '/mes/sop/binding', method: 'post', data });

/** 批量删除绑定 */
export const delBinding = (bindingIds: number | number[]) => request({ url: `/mes/sop/binding/${bindingIds}`, method: 'delete' });

/** 查询工序绑定的SOP列表 */
export const listBindingByProcess = (processId: number | string): AxiosPromise<SopProcessBindingVO[]> =>
  request({ url: `/mes/sop/binding/by-process/${processId}`, method: 'get' });
