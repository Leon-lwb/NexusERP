import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ProductionReportQuery, WorkReportForm, WorkReportQuery, WorkReportVO } from './types';

/** 生产统计报表（非报工单 CRUD，对应 PrdProductionReportController 的统计端点） */
export function getWorkOrderCompletionRate(query: ProductionReportQuery) {
  return request({ url: '/erp/prd/report/completionRate', method: 'get', params: query });
}

export function getProductionEfficiency(query: ProductionReportQuery) {
  return request({ url: '/erp/prd/report/efficiency', method: 'get', params: query });
}

// ==================== 生产报工单 CRUD（PrdWorkReportController，/erp/prd/report） ====================
// 端点与权限码一一对应：
//   GET    /erp/prd/report/list      prd:report:list
//   POST   /erp/prd/report/export    prd:report:export
//   GET    /erp/prd/report/{id}      prd:report:query
//   POST   /erp/prd/report           prd:report:add
//   PUT    /erp/prd/report           prd:report:edit
//   DELETE /erp/prd/report/{ids}     prd:report:remove

const REPORT_BASE = '/erp/prd/report';

/** 分页查询生产报工列表 */
export function listWorkReport(query: WorkReportQuery): AxiosPromise<WorkReportVO[]> {
  return request({ url: REPORT_BASE + '/list', method: 'get', params: query });
}

/** 查询生产报工详情 */
export function getWorkReport(reportId: number | string): AxiosPromise<WorkReportVO> {
  return request({ url: REPORT_BASE + '/' + reportId, method: 'get' });
}

/** 新增生产报工 */
export function addWorkReport(data: WorkReportForm) {
  return request({ url: REPORT_BASE, method: 'post', data });
}

/** 修改生产报工 */
export function updateWorkReport(data: WorkReportForm) {
  return request({ url: REPORT_BASE, method: 'put', data });
}

/** 批量删除生产报工 */
export function delWorkReport(reportIds: number | string | Array<number | string>) {
  return request({ url: REPORT_BASE + '/' + reportIds, method: 'delete' });
}
