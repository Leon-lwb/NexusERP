import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 工装视图对象 */
export interface TgToolingVO {
  toolingId: number;
  toolingCode: string;
  toolingName: string;
  toolingType: string;
  spec?: string;
  workshopId?: number;
  workshopName?: string;
  status: string;
  totalLifeCount?: number;
  totalLifeHours?: number;
  usedCount?: number;
  usedHours?: number;
  lastMaintainTime?: string;
  purchaseDate?: string;
  purchaseCost?: number;
  remark?: string;
  remainingCount?: number;
  remainingHours?: number;
}

/** 工装查询条件 */
export interface TgToolingQuery extends PageQuery {
  toolingCode?: string;
  toolingName?: string;
  toolingType?: string;
  status?: string;
  workshopId?: number;
}

/** 工装表单 */
export interface TgToolingForm {
  toolingId?: number;
  toolingCode?: string;
  toolingName: string;
  toolingType: string;
  spec?: string;
  workshopId?: number;
  workshopName?: string;
  totalLifeCount?: number;
  totalLifeHours?: number;
  purchaseDate?: string;
  purchaseCost?: number;
  remark?: string;
}

/** 领用入参 */
export interface TgCheckoutBo {
  operatorId: number;
  operatorName?: string;
  woId?: number;
  woNo?: string;
  workstationId?: number;
  workstationName?: string;
  remark?: string;
}

/** 保养完成入参 */
export interface TgMaintainCompleteBo {
  planId?: number;
  maintainType?: string;
  maintainTime?: string;
  maintainerId?: number;
  maintainerName?: string;
  description?: string;
  cost?: number;
  remark?: string;
}

/** 分页查询工装列表 */
export const listTooling = (query?: TgToolingQuery): AxiosPromise<TgToolingVO[]> =>
  request({ url: '/mes/tg/tooling/list', method: 'get', params: query });

/** 保养到期预警 */
export const listMaintenanceDue = (): AxiosPromise<TgToolingVO[]> => request({ url: '/mes/tg/tooling/due', method: 'get' });

/** 获取工装详情 */
export const getTooling = (toolingId: number | string): AxiosPromise<TgToolingVO> => request({ url: `/mes/tg/tooling/${toolingId}`, method: 'get' });

/** 新增工装 */
export const addTooling = (data: TgToolingForm) => request({ url: '/mes/tg/tooling', method: 'post', data });

/** 修改工装 */
export const updateTooling = (data: TgToolingForm) => request({ url: '/mes/tg/tooling', method: 'put', data });

/** 批量删除工装 */
export const delTooling = (toolingIds: number | number[]) => request({ url: `/mes/tg/tooling/${toolingIds}`, method: 'delete' });

/** 领用: IDLE -> IN_USE */
export const checkoutTooling = (toolingId: number | string, data: TgCheckoutBo) =>
  request({ url: `/mes/tg/tooling/checkout/${toolingId}`, method: 'put', data });

/** 归还: IN_USE -> IDLE */
export const returnTooling = (toolingId: number | string, params: { usageCount?: number; usageHours?: number; remark?: string }) =>
  request({ url: `/mes/tg/tooling/return/${toolingId}`, method: 'put', params });

/** 开始保养: IDLE -> MAINTENANCE */
export const startMaintainTooling = (toolingId: number | string, remark?: string) =>
  request({ url: `/mes/tg/tooling/maintain/start/${toolingId}`, method: 'put', params: { remark } });

/** 完成保养: MAINTENANCE -> IDLE(归零) */
export const completeMaintainTooling = (toolingId: number | string, data: TgMaintainCompleteBo) =>
  request({ url: `/mes/tg/tooling/maintain/complete/${toolingId}`, method: 'put', data });

/** 报废: -> SCRAPPED */
export const scrapTooling = (toolingId: number | string, reason?: string) =>
  request({ url: `/mes/tg/tooling/scrap/${toolingId}`, method: 'put', params: { reason } });

/** 校验工装可用性(契约) */
export const checkToolingUsable = (toolingId: number | string): AxiosPromise<Record<string, any>> =>
  request({ url: `/mes/tg/tooling/check/${toolingId}`, method: 'get' });
