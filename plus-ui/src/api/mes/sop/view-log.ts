import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** SOP查阅记录视图对象 */
export interface SopViewLogVO {
  logId: number;
  sopId: number;
  sopName?: string;
  versionNo?: string;
  viewerId: number;
  viewerName?: string;
  viewTime: string;
  woId?: number | string;
  woNo?: string;
  processId?: number | string;
  processName?: string;
  workstationId?: number | string;
  signatureId?: number;
  ipAddress?: string;
  userAgent?: string;
  createTime?: string;
}

/** SOP查阅记录查询条件 */
export interface SopViewLogQuery extends PageQuery {
  sopId?: number;
  viewerId?: number;
  woId?: number;
  processId?: number;
  versionNo?: string;
}

/** 分页查询查阅记录列表 */
export const listViewLog = (query?: SopViewLogQuery): AxiosPromise<SopViewLogVO[]> =>
  request({ url: '/mes/sop/view-log/list', method: 'get', params: query });

/** 登记查阅(查看器打开时调) */
export const recordViewLog = (params: {
  sopId: number;
  versionNo?: string;
  woId?: number | string;
  woNo?: string;
  processId?: number | string;
  processName?: string;
  workstationId?: number | string;
}) => request({ url: '/mes/sop/view-log', method: 'post', params });

/** 批量删除查阅记录 */
export const delViewLog = (logIds: number | number[]) => request({ url: `/mes/sop/view-log/${logIds}`, method: 'delete' });
