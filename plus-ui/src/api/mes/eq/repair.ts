import request, { download } from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 维修记录视图对象 */
export interface EqRepairVO {
  repairId: number;
  repairNo: string;
  equipmentId: number;
  equipmentCode?: string;
  equipmentName?: string;
  faultType?: string;
  faultDesc?: string;
  reporterId?: number;
  reporterName?: string;
  repairerId?: number;
  repairerName?: string;
  repairStart?: string;
  repairEnd?: string;
  downtimeHours?: number;
  repairCost?: number;
  status: string;
  remark?: string;
}

/** 维修记录查询条件 */
export interface EqRepairQuery extends PageQuery {
  repairNo?: string;
  equipmentId?: number;
  status?: string;
  faultType?: string;
  startDate?: string;
  endDate?: string;
}

/** 维修报修表单 */
export interface EqRepairForm {
  equipmentId: number;
  faultType?: string;
  faultDesc?: string;
  remark?: string;
}

/** 分页查询维修记录 */
export const listRepair = (query?: EqRepairQuery): AxiosPromise<EqRepairVO[]> =>
  request({ url: '/mes/eq/repair/list', method: 'get', params: query });

/** 获取维修详情 */
export const getRepair = (repairId: string | number): AxiosPromise<EqRepairVO> => request({ url: `/mes/eq/repair/${repairId}`, method: 'get' });

/** 报修(新增维修单) */
export const addRepair = (data: EqRepairForm): AxiosPromise<EqRepairVO> => request({ url: '/mes/eq/repair', method: 'post', data });

/** 维修开工 */
export const startRepair = (repairId: string | number) => request({ url: `/mes/eq/repair/start/${repairId}`, method: 'put' });

/** 维修完成 */
export const completeRepair = (repairId: string | number, downtimeHours?: number, repairCost?: number, remark?: string) =>
  request({ url: `/mes/eq/repair/complete/${repairId}`, method: 'put', params: { downtimeHours, repairCost, remark } });

/** 导出维修记录 */
export const exportRepair = (query?: EqRepairQuery) => {
  download('/mes/eq/repair/export', { ...query }, `repair_${new Date().getTime()}.xlsx`);
};

/** 故障统计分析 */
export interface EqRepairStatisticsVO {
  totalFault?: number;
  completedCount?: number;
  repairingCount?: number;
  totalDowntimeHours?: number;
  totalRepairCost?: number;
  mttrHours?: number;
  mtbfHours?: number;
  trend?: Array<{ statDate: string; faultCount: number; downtimeHours?: number }>;
  pareto?: Array<{ faultType: string; count: number; ratio?: number }>;
  topEquipment?: Array<{ equipmentId: number; equipmentCode?: string; equipmentName?: string; faultCount: number; downtimeHours?: number }>;
}

export const getRepairStatistics = (params?: { startDate?: string; endDate?: string }): AxiosPromise<EqRepairStatisticsVO> =>
  request({ url: '/mes/eq/repair/statistics', method: 'get', params });
