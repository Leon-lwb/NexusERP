import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 缺陷代码视图对象 */
export interface NcrDefectCodeVO {
  codeId: number;
  parentId?: number;
  code: string;
  name: string;
  level?: number;
  enabled: string;
  sortOrder?: number;
  remark?: string;
  children?: NcrDefectCodeVO[];
}

/** 缺陷代码查询条件 */
export interface NcrDefectCodeQuery extends PageQuery {
  code?: string;
  name?: string;
  enabled?: string;
}

/** 缺陷代码表单 */
export interface NcrDefectCodeForm {
  codeId?: number;
  parentId?: number;
  code: string;
  name: string;
  level?: number;
  enabled?: string;
  sortOrder?: number;
  remark?: string;
}

/** 分页查询缺陷代码列表 */
export const listDefectCode = (query?: NcrDefectCodeQuery): AxiosPromise<NcrDefectCodeVO[]> =>
  request({ url: '/mes/ncr/defect-code/list', method: 'get', params: query });

/** 查询缺陷代码树(全部,不分页) */
export const listDefectCodeTree = (): AxiosPromise<NcrDefectCodeVO[]> => request({ url: '/mes/ncr/defect-code/tree', method: 'get' });

/** 获取缺陷代码详情 */
export const getDefectCode = (codeId: number | string): AxiosPromise<NcrDefectCodeVO> =>
  request({ url: `/mes/ncr/defect-code/${codeId}`, method: 'get' });

/** 新增缺陷代码 */
export const addDefectCode = (data: NcrDefectCodeForm) => request({ url: '/mes/ncr/defect-code', method: 'post', data });

/** 修改缺陷代码 */
export const updateDefectCode = (data: NcrDefectCodeForm) => request({ url: '/mes/ncr/defect-code', method: 'put', data });

/** 删除缺陷代码 */
export const delDefectCode = (codeIds: number | number[]) => request({ url: `/mes/ncr/defect-code/${codeIds}`, method: 'delete' });
