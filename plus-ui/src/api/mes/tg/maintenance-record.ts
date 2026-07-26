import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 保养记录视图对象 */
export interface TgMaintenanceRecordVO {
  recordId: number;
  toolingId: number;
  planId?: number;
  maintainType?: string;
  maintainTime: string;
  maintainerId?: number;
  maintainerName?: string;
  usedCountBefore?: number;
  usedCountAfter?: number;
  usedHoursBefore?: number;
  usedHoursAfter?: number;
  description?: string;
  cost?: number;
  remark?: string;
}

/** 保养记录查询条件 */
export interface TgMaintenanceRecordQuery extends PageQuery {
  toolingId?: number;
  planId?: number;
  maintainType?: string;
}

/** 保养记录表单 */
export interface TgMaintenanceRecordForm {
  recordId?: number;
  toolingId: number;
  planId?: number;
  maintainType?: string;
  maintainTime?: string;
  maintainerId?: number;
  maintainerName?: string;
  usedCountBefore?: number;
  usedCountAfter?: number;
  usedHoursBefore?: number;
  usedHoursAfter?: number;
  description?: string;
  cost?: number;
  remark?: string;
}

/** 分页查询保养记录 */
export const listMaintenanceRecord = (query?: TgMaintenanceRecordQuery): AxiosPromise<TgMaintenanceRecordVO[]> =>
  request({ url: '/mes/tg/maintenance-record/list', method: 'get', params: query });

/** 获取保养记录详情 */
export const getMaintenanceRecord = (recordId: number | string): AxiosPromise<TgMaintenanceRecordVO> =>
  request({ url: `/mes/tg/maintenance-record/${recordId}`, method: 'get' });

/** 新增保养记录 */
export const addMaintenanceRecord = (data: TgMaintenanceRecordForm) => request({ url: '/mes/tg/maintenance-record', method: 'post', data });

/** 修改保养记录 */
export const updateMaintenanceRecord = (data: TgMaintenanceRecordForm) => request({ url: '/mes/tg/maintenance-record', method: 'put', data });

/** 批量删除保养记录 */
export const delMaintenanceRecord = (recordIds: number | number[]) => request({ url: `/mes/tg/maintenance-record/${recordIds}`, method: 'delete' });
