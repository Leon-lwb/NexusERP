import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** NCR不合格品视图对象 */
export interface NcrOrderVO {
  ncrId: number;
  ncrCode?: string;
  sourceType?: string;
  sourceTypeName?: string;
  woId?: number;
  woNo?: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  defectQty?: number;
  status?: string;
  disposition?: string;
  reviewBy?: number;
  reviewByName?: string;
  reviewTime?: string;
  dispositionBy?: number;
  dispositionByName?: string;
  dispositionTime?: string;
  remark?: string;
  createTime?: string;
  createBy?: string;
}

/** NCR不合格品查询条件 */
export interface NcrOrderQuery extends PageQuery {
  ncrCode?: string;
  status?: string;
  sourceType?: string;
  woNo?: string;
  materialId?: number;
}

/** NCR不合格品表单 */
export interface NcrOrderForm {
  ncrId?: number;
  ncrCode?: string;
  sourceType?: string;
  woId?: number;
  woNo?: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  defectQty?: number;
  remark?: string;
}

/** 分页查询NCR列表 */
export const listOrder = (query?: NcrOrderQuery): AxiosPromise<NcrOrderVO[]> => request({ url: '/mes/ncr/order/list', method: 'get', params: query });

/** 获取NCR详情 */
export const getOrder = (ncrId: number | string): AxiosPromise<NcrOrderVO> => request({ url: `/mes/ncr/order/${ncrId}`, method: 'get' });

/** 新增NCR */
export const addOrder = (data: NcrOrderForm) => request({ url: '/mes/ncr/order', method: 'post', data });

/** 修改NCR */
export const updateOrder = (data: NcrOrderForm) => request({ url: '/mes/ncr/order', method: 'put', data });

/** 批量删除NCR */
export const delOrder = (ncrIds: number | number[]) => request({ url: `/mes/ncr/order/${ncrIds}`, method: 'delete' });

/** NCR评审(通过) */
export const reviewOrder = (ncrId: number | string) => request({ url: `/mes/ncr/order/review/${ncrId}`, method: 'put' });

/** NCR处置 */
export const disposeOrder = (ncrId: number | string, disposition: string, remark?: string) =>
  request({ url: `/mes/ncr/order/dispose/${ncrId}`, method: 'put', params: { disposition, remark } });
