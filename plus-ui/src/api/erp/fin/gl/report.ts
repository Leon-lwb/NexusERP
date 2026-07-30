import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 试算平衡表行 */
export interface TrialBalanceVO {
  accountId: number;
  accountCode: string;
  accountName: string;
  accountType: string;
  balanceDir: string;
  openingDebit: number;
  openingCredit: number;
  periodDebit: number;
  periodCredit: number;
  closingDebit: number;
  closingCredit: number;
}

/** 试算平衡表查询 */
export const trialBalance = (params: { periodId: number; accountType?: string }): AxiosPromise<TrialBalanceVO[]> => {
  return request({ url: '/erp/fin/gl/report/trialBalance', method: 'get', params });
};
