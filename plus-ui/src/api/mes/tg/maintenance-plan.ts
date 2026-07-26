import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 保养计划视图对象 */
export interface TgMaintenancePlanVO {
  planId: number;
  toolingId: number;
  planName: string;
  triggerType: string;
  triggerValue?: number;
  lastMaintainTime?: string;
  nextMaintainTime?: string;
  nextMaintainCount?: number;
  nextMaintainHours?: number;
  enabled: string;
  remark?: string;
}

/** 保养计划查询条件 */
export interface TgMaintenancePlanQuery extends PageQuery {
  toolingId?: number;
  planName?: string;
  triggerType?: string;
  enabled?: string;
}

/** 保养计划表单 */
export interface TgMaintenancePlanForm {
  planId?: number;
  toolingId: number;
  planName: string;
  triggerType: string;
  triggerValue?: number;
  lastMaintainTime?: string;
  nextMaintainTime?: string;
  nextMaintainCount?: number;
  nextMaintainHours?: number;
  enabled?: string;
  remark?: string;
}

/** 分页查询保养计划 */
export const listMaintenancePlan = (query?: TgMaintenancePlanQuery): AxiosPromise<TgMaintenancePlanVO[]> =>
  request({ url: '/mes/tg/maintenance-plan/list', method: 'get', params: query });

/** 获取保养计划详情 */
export const getMaintenancePlan = (planId: number | string): AxiosPromise<TgMaintenancePlanVO> =>
  request({ url: `/mes/tg/maintenance-plan/${planId}`, method: 'get' });

/** 新增保养计划 */
export const addMaintenancePlan = (data: TgMaintenancePlanForm) => request({ url: '/mes/tg/maintenance-plan', method: 'post', data });

/** 修改保养计划 */
export const updateMaintenancePlan = (data: TgMaintenancePlanForm) => request({ url: '/mes/tg/maintenance-plan', method: 'put', data });

/** 批量删除保养计划 */
export const delMaintenancePlan = (planIds: number | number[]) => request({ url: `/mes/tg/maintenance-plan/${planIds}`, method: 'delete' });
