import request, { download } from '@/utils/request';
import { AxiosPromise } from 'axios';

/** OEE 聚合记录视图对象 */
export interface EqOeeRecordVO {
  recordId: number;
  equipmentId: number;
  equipmentCode?: string;
  equipmentName?: string;
  equipmentType?: string;
  workcenterId?: number;
  statDate: string;
  plannedMinutes?: number;
  downtimeMinutes?: number;
  maintenanceMinutes?: number;
  runMinutes?: number;
  faultCount?: number;
  availability?: number;
  performanceRate?: number;
  qualityRate?: number;
  oee?: number;
  remark?: string;
}

/** OEE 看板查询条件 */
export interface EqOeeQuery extends PageQuery {
  startDate?: string;
  endDate?: string;
  equipmentId?: number;
  workcenterId?: number;
  equipmentType?: string;
  granularity?: string;
}

/** OEE 概览 KPI */
export interface EqOeeOverviewVO {
  avgAvailability?: number;
  avgOee?: number;
  totalDowntimeMinutes?: number;
  totalFaultCount?: number;
  equipmentCount?: number;
  totalPlannedMinutes?: number;
  totalRunMinutes?: number;
  /**
   * 表现率是否已有真实数据源（MES-EQ-03）。
   * false 表示表现率仍为占位（后端恒置 false），前端必须展示「占位」而不是 100%。
   */
  performanceRateAvailable?: boolean;
  /** 质量率是否已有真实数据源（区间内至少一条记录有质量率） */
  qualityRateAvailable?: boolean;
}

/** OEE 趋势点 */
export interface EqOeeTrendVO {
  statDate: string;
  availability?: number;
  oee?: number;
  downtimeMinutes?: number;
  faultCount?: number;
}

/** OEE 时长分布 */
export interface EqOeeDistributionVO {
  runMinutes?: number;
  downtimeMinutes?: number;
  maintenanceMinutes?: number;
  runRatio?: number;
  downtimeRatio?: number;
  maintenanceRatio?: number;
}

/** OEE 停机设备 TOP */
export interface EqOeeTopVO {
  equipmentId: number;
  equipmentCode?: string;
  equipmentName?: string;
  downtimeMinutes?: number;
  faultCount?: number;
  avgAvailability?: number;
}

/** 看板概览 KPI */
export const getOeeOverview = (params?: EqOeeQuery): AxiosPromise<EqOeeOverviewVO> => request({ url: '/mes/eq/oee/overview', method: 'get', params });

/** 看板趋势 */
export const getOeeTrend = (params?: EqOeeQuery): AxiosPromise<EqOeeTrendVO[]> => request({ url: '/mes/eq/oee/trend', method: 'get', params });

/** 看板时长分布 */
export const getOeeDistribution = (params?: EqOeeQuery): AxiosPromise<EqOeeDistributionVO> =>
  request({ url: '/mes/eq/oee/distribution', method: 'get', params });

/** 看板停机设备 TOP10 */
export const getOeeTopDowntime = (params?: EqOeeQuery): AxiosPromise<EqOeeTopVO[]> =>
  request({ url: '/mes/eq/oee/topDowntime', method: 'get', params });

/** 分页查询 OEE 明细记录 */
export const listOeeRecord = (query?: EqOeeQuery): AxiosPromise<EqOeeRecordVO[]> =>
  request({ url: '/mes/eq/oee/list', method: 'get', params: query });

/** 导出 OEE 明细数据 */
export const exportOee = (query?: EqOeeQuery) => download('/mes/eq/oee/export', { ...query }, `oee_${new Date().getTime()}.xlsx`);

/** 手动聚合指定日期(date 为空聚合昨天) */
export const aggregateOee = (date?: string): AxiosPromise<number> =>
  request({ url: '/mes/eq/oee/aggregate', method: 'post', params: date ? { date } : {} });
