import request from '@/utils/request';
import { SalesReportQuery, SalesExecutionRow, CustomerRankingRow } from './types';

/**
 * 销售执行率报表（按月：订单数 / 销售额 / 已完成数 / 完成率）。
 * <p>后端权限码为 `so:orderheader:list`（报表无独立权限码，沿用销售订单列表权限）。</p>
 */
export function getSalesExecution(query: SalesReportQuery) {
  return request<SalesExecutionRow[]>({ url: '/erp/so/report/execution', method: 'get', params: query });
}

/** 客户销售排行 TOP N */
export function getCustomerRanking(query: SalesReportQuery) {
  return request<CustomerRankingRow[]>({ url: '/erp/so/report/customerRanking', method: 'get', params: query });
}
