import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 使用记录视图对象 */
export interface TgUsageRecordVO {
  usageId: number;
  toolingId: number;
  woId?: number;
  woNo?: string;
  workstationId?: number;
  workstationName?: string;
  operatorId?: number;
  operatorName?: string;
  checkoutTime: string;
  returnTime?: string;
  usageCount?: number;
  usageHours?: number;
  status: string;
  remark?: string;
}

/** 使用记录查询条件 */
export interface TgUsageRecordQuery extends PageQuery {
  toolingId?: number;
  woId?: number;
  operatorId?: number;
  status?: string;
}

/** 使用记录表单 */
export interface TgUsageRecordForm {
  usageId?: number;
  toolingId: number;
  woId?: number;
  woNo?: string;
  workstationId?: number;
  workstationName?: string;
  operatorId?: number;
  operatorName?: string;
  usageCount?: number;
  usageHours?: number;
  status?: string;
  remark?: string;
}

/** 分页查询使用记录 */
export const listUsageRecord = (query?: TgUsageRecordQuery): AxiosPromise<TgUsageRecordVO[]> =>
  request({ url: '/mes/tg/usage-record/list', method: 'get', params: query });

/** 获取使用记录详情 */
export const getUsageRecord = (usageId: number | string): AxiosPromise<TgUsageRecordVO> =>
  request({ url: `/mes/tg/usage-record/${usageId}`, method: 'get' });

/** 新增使用记录 */
export const addUsageRecord = (data: TgUsageRecordForm) => request({ url: '/mes/tg/usage-record', method: 'post', data });

/** 修改使用记录 */
export const updateUsageRecord = (data: TgUsageRecordForm) => request({ url: '/mes/tg/usage-record', method: 'put', data });

/** 批量删除使用记录 */
export const delUsageRecord = (usageIds: number | number[]) => request({ url: `/mes/tg/usage-record/${usageIds}`, method: 'delete' });
