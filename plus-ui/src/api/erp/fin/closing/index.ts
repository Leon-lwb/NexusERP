import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 关账步骤状态 */
export interface ClosingStepVO {
  periodId: number;
  periodName: string;
  periodStatus: string;
  trialChecked: boolean;
  carried: boolean;
  revaluated: boolean;
  closed: boolean;
  voucherCount: number;
  closingVoucherNo: string;
  revaluationVoucherNo: string;
  logs: any[];
}

/** 关账日志 */
export interface FinCloseLogVO {
  logId: number;
  periodId: number;
  closeType: string;
  step: string;
  status: string;
  message: string;
  operatorId: number;
  createTime: string;
}

/** 月结向导步骤状态 */
export const closingSteps = (params: { periodId: number }): AxiosPromise<ClosingStepVO> => {
  return request({ url: '/erp/fin/closing/steps', method: 'get', params });
};

/** 试算校验 */
export const trialCheck = (data: { periodId: number }): AxiosPromise<any> => {
  return request({ url: '/erp/fin/closing/trialCheck', method: 'post', data });
};

/** 损益结转 */
export const carryPnl = (data: { periodId: number }): AxiosPromise<any> => {
  return request({ url: '/erp/fin/closing/carryPnl', method: 'post', data });
};

/** 外币期末重估 */
export const revaluate = (data: { periodId: number }): AxiosPromise<any> => {
  return request({ url: '/erp/fin/closing/revaluate', method: 'post', data });
};

/** 月结整体执行 */
export const monthlyClose = (data: { periodId: number }): AxiosPromise<any> => {
  return request({ url: '/erp/fin/closing/close', method: 'post', data });
};

/** 年结 */
export const yearlyClose = (data: { fiscalYear: number }): AxiosPromise<any> => {
  return request({ url: '/erp/fin/closing/yearly', method: 'post', data });
};

/** 关账日志 */
export const closeLogs = (params: { periodId: number }): AxiosPromise<FinCloseLogVO[]> => {
  return request({ url: '/erp/fin/closing/logs', method: 'get', params });
};
