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
  /** 校准周期(天), 空=未纳入校准管理 (MES-TG-01) */
  calibrationCycleDays?: number;
  /** 上次校准日期 (MES-TG-01) */
  lastCalibrationDate?: string;
  /** 下次校准日期 (MES-TG-01) */
  nextCalibrationDate?: string;
  /** 校准结果: PASS/FAIL/ADJUSTED (MES-TG-01) */
  calibrationResult?: string;
  /** 校准备注 (MES-TG-01) */
  calibrationRemark?: string;
  /** 校准是否已超期(非库列, 服务端填充, MES-TG-01) */
  calibrationOverdue?: boolean;
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
  /** 校准周期(天), 空=未纳入校准管理 (MES-TG-01) */
  calibrationCycleDays?: number;
  /** 上次校准日期 (MES-TG-01) */
  lastCalibrationDate?: string;
  /** 下次校准日期 (MES-TG-01) */
  nextCalibrationDate?: string;
  /** 校准结果: PASS/FAIL/ADJUSTED (MES-TG-01) */
  calibrationResult?: string;
  /** 校准备注 (MES-TG-01) */
  calibrationRemark?: string;
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

/**
 * 校准登记入参（MES-TG-01，后端 TgCalibrationBo）
 * <p>下次校准日由服务端按「校准日期 + 周期」计算，客户端不得直传，
 * 否则可把到期日改到未来从而绕过超期拦截。</p>
 */
export interface TgCalibrationBo {
  /** 校准周期(天), 留空沿用台账上的 calibrationCycleDays */
  calibrationCycleDays?: number;
  /** 校准日期(YYYY-MM-DD), 留空取当前时间 */
  calibrationDate?: string;
  /** 校准结果: PASS(默认)/FAIL/ADJUSTED；FAIL 时服务端把下次校准日置为本次日期，工装立即处于超期不可用态 */
  calibrationResult?: string;
  /** 校准人ID, 留空取当前登录人 */
  calibratedBy?: number;
  /** 校准备注 */
  calibrationRemark?: string;
  /** 工装备注(可选, 非空时一并更新) */
  remark?: string;
}

/** 校准到期/超期预警(今天 ~ 今天+7 天, 含已超期) (MES-TG-01/TG-05) */
export const listCalibrationDue = (): AxiosPromise<TgToolingVO[]> => request({ url: '/mes/tg/tooling/calibration/due', method: 'get' });

/** 登记校准: IDLE -> CALIBRATING -> IDLE(服务端计算下次校准日) (MES-TG-01) */
export const registerCalibration = (toolingId: number | string, data: TgCalibrationBo) =>
  request({ url: `/mes/tg/tooling/calibration/${toolingId}`, method: 'put', data });
