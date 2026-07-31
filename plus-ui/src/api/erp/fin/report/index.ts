import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 报表行(三大报表统一) */
export interface ReportLineVO {
  reportType: string;
  itemCode: string;
  itemName: string;
  parentItemId: number;
  isSummary: boolean;
  sortOrder: number;
  amount: number;
  yearAmount: number;
}

/** 报表项目映射 */
export interface ReportItemVO {
  itemId: number;
  reportType: string;
  itemCode: string;
  itemName: string;
  sortOrder: number;
  parentItemId: number;
  isSummary: string;
  accountCodeMatch: string;
  sign: string;
  status: string;
}

/** 资产负债表 */
export const balanceSheet = (params: { periodId: number; includeZero?: boolean }): AxiosPromise<ReportLineVO[]> => {
  return request({ url: '/erp/fin/report/balance', method: 'get', params });
};

/** 利润表 */
export const incomeStatement = (params: { periodId: number; yearToDate?: boolean }): AxiosPromise<ReportLineVO[]> => {
  return request({ url: '/erp/fin/report/income', method: 'get', params });
};

/** 现金流量表 */
export const cashFlowStatement = (params: { periodId: number; yearToDate?: boolean }): AxiosPromise<ReportLineVO[]> => {
  return request({ url: '/erp/fin/report/cashFlow', method: 'get', params });
};

/** 报表项目映射查询 */
export const listItemConfig = (reportType?: string): AxiosPromise<ReportItemVO[]> => {
  return request({ url: '/erp/fin/report/itemConfig', method: 'get', params: { reportType } });
};

/** 报表项目映射保存 */
export const saveItemConfig = (data: Partial<ReportItemVO>): AxiosPromise<any> => {
  return request({ url: '/erp/fin/report/itemConfig', method: 'post', data });
};

/** 报表项目映射删除 */
export const deleteItemConfig = (itemIds: number[]): AxiosPromise<any> => {
  return request({ url: `/erp/fin/report/itemConfig/${itemIds.join(',')}`, method: 'delete' });
};
