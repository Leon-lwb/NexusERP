import request, { download } from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 保养计划视图对象 */
export interface EqMaintenancePlanVO {
  planId: number;
  planName: string;
  equipmentId: number;
  maintenanceType: string;
  cycleDays?: number;
  nextDate?: string;
  content?: string;
  responsibleId?: number;
  responsibleName?: string;
  status: string;
  remark?: string;
}

/** 保养计划查询条件 */
export interface EqMaintenancePlanQuery extends PageQuery {
  planName?: string;
  equipmentId?: number;
  maintenanceType?: string;
  status?: string;
}

/** 保养计划表单 */
export interface EqMaintenancePlanForm {
  planId?: number;
  planName: string;
  equipmentId: number;
  maintenanceType: string;
  cycleDays?: number;
  nextDate?: string;
  content?: string;
  responsibleId?: number;
  responsibleName?: string;
  status?: string;
  remark?: string;
}

/** 分页查询保养计划 */
export const listMaintenancePlan = (query?: EqMaintenancePlanQuery): AxiosPromise<EqMaintenancePlanVO[]> =>
  request({ url: '/mes/eq/maintenance-plan/list', method: 'get', params: query });

/** 获取保养计划详情 */
export const getMaintenancePlan = (planId: string | number): AxiosPromise<EqMaintenancePlanVO> =>
  request({ url: `/mes/eq/maintenance-plan/${planId}`, method: 'get' });

/** 新增保养计划 */
export const addMaintenancePlan = (data: EqMaintenancePlanForm): AxiosPromise<EqMaintenancePlanVO> =>
  request({ url: '/mes/eq/maintenance-plan', method: 'post', data });

/** 编辑保养计划 */
export const updateMaintenancePlan = (data: EqMaintenancePlanForm): AxiosPromise<EqMaintenancePlanVO> =>
  request({ url: '/mes/eq/maintenance-plan', method: 'put', data });

/** 批量删除保养计划 */
export const delMaintenancePlan = (ids: (string | number)[]) => request({ url: `/mes/eq/maintenance-plan/${ids.join(',')}`, method: 'delete' });

/** 完成保养(写入执行记录 + 推进下次保养日期) */
export const completeMaintenancePlan = (planId: string | number, data: { content?: string; result?: string; remark?: string }): AxiosPromise<any> =>
  request({ url: `/mes/eq/maintenance-plan/${planId}/complete`, method: 'post', data });

/** 导出保养计划 */
export const exportMaintenancePlan = (query?: EqMaintenancePlanQuery) => {
  download('/mes/eq/maintenance-plan/export', { ...query }, `maintenance_plan_${new Date().getTime()}.xlsx`);
};
