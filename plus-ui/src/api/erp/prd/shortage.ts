import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 缺料风险预测视图对象(推演型) */
export interface PrdShortageRiskVO {
  materialId: number;
  materialCode?: string;
  materialName?: string;
  stockQty?: number;
  safetyStock?: number;
  totalDemand?: number;
  onOrderQty?: number;
  earliestShortageDate?: string;
  daysUntilShortage?: number | null;
  maxGap?: number;
  gapDays?: number;
  suggestion?: string;
  suggestionText?: string;
  hasRisk?: boolean;
}

/** 缺料风险预测(默认推演30天, 按最早缺料日升序) */
export const predictShortage = (days?: number): AxiosPromise<PrdShortageRiskVO[]> =>
  request({ url: '/erp/prd/shortage/predict', method: 'get', params: { days } });

/** 强制刷新(跳过缓存重新推演) */
export const predictShortageRefresh = (days?: number): AxiosPromise<PrdShortageRiskVO[]> =>
  request({ url: '/erp/prd/shortage/predict/refresh', method: 'get', params: { days } });
