import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 批次视图对象 */
export interface LtLotVO {
  lotId: number;
  lotCode: string;
  materialId: number;
  materialCode?: string;
  materialName?: string;
  supplierId?: number;
  supplierName?: string;
  supplierLot?: string;
  quantity: number;
  consumedQty: number;
  remainingQty?: number;
  producedDate?: string;
  expiryDate?: string;
  status: string;
  qcStatus: string;
  iqcId?: number;
  remark?: string;
}

/** 批次查询条件 */
export interface LtLotQuery extends PageQuery {
  lotCode?: string;
  materialId?: number;
  supplierId?: number;
  supplierLot?: string;
  status?: string;
  qcStatus?: string;
}

/** 批次表单 */
export interface LtLotForm {
  lotId?: number;
  lotCode?: string;
  materialId: number;
  materialCode?: string;
  materialName?: string;
  supplierId?: number;
  supplierName?: string;
  supplierLot?: string;
  quantity: number;
  producedDate?: string;
  expiryDate?: string;
  iqcId?: number;
  remark?: string;
}

/** 批次追溯结果 */
export interface LtLotTraceResult {
  lot: LtLotVO;
  consumptions: any[];
  bindings: any[];
  woIds: number[];
  snIds: number[];
}

/** 分页查询批次列表 */
export const listLot = (query?: LtLotQuery): AxiosPromise<LtLotVO[]> => request({ url: '/mes/lt/lot/list', method: 'get', params: query });

/** 获取批次详情 */
export const getLot = (lotId: number | string): AxiosPromise<LtLotVO> => request({ url: `/mes/lt/lot/${lotId}`, method: 'get' });

/** 根据批次编码查询 */
export const getLotByCode = (lotCode: string): AxiosPromise<LtLotVO> => request({ url: `/mes/lt/lot/by-code/${lotCode}`, method: 'get' });

/** 新增批次 */
export const addLot = (data: LtLotForm) => request({ url: '/mes/lt/lot', method: 'post', data });

/** 修改批次 */
export const updateLot = (data: LtLotForm) => request({ url: '/mes/lt/lot', method: 'put', data });

/** 批量删除批次 */
export const delLot = (lotIds: number | number[]) => request({ url: `/mes/lt/lot/${lotIds}`, method: 'delete' });

/** 批次隔离 */
export const quarantineLot = (lotId: number | string, remark?: string) =>
  request({ url: `/mes/lt/lot/quarantine/${lotId}`, method: 'put', params: { remark } });

/** 批次报废 */
export const scrapLot = (lotId: number | string, remark?: string) =>
  request({ url: `/mes/lt/lot/scrap/${lotId}`, method: 'put', params: { remark } });

/** 批次放行(解除隔离) */
export const releaseLot = (lotId: number | string) => request({ url: `/mes/lt/lot/release/${lotId}`, method: 'put' });

/** 更新批次QC状态 */
export const updateLotQcStatus = (lotId: number | string, qcStatus: string) =>
  request({ url: `/mes/lt/lot/qc-status/${lotId}`, method: 'put', params: { qcStatus } });

/** FEFO 推荐 */
export const recommendFefo = (materialId: number | string, requiredQty?: number): AxiosPromise<LtLotVO[]> =>
  request({ url: `/mes/lt/lot/fefo/${materialId}`, method: 'get', params: { requiredQty } });

/** 批次追溯链 */
export const traceLot = (lotId: number | string): AxiosPromise<LtLotTraceResult> => request({ url: `/mes/lt/lot/trace/${lotId}`, method: 'get' });
