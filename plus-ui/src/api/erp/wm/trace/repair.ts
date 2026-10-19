import request from '@/utils/request';
import type { AxiosPromise } from 'axios';

/** 审计运行只展示服务端事实，审阅清单由服务端详情提供。 */
export interface TraceRepairRun {
  runId: string;
  status: string;
  target?: string;
  createdAt?: string;
  createTime?: string;
  summary?: string;
  manifestDigest?: string;
  reviewedManifest?: unknown;
  legacyGaps?: unknown[];
  items?: Record<string, unknown>[];
  [key: string]: unknown;
}

/** 获取已有审计/修复记录，不触发修复。 */
export const listRepairRuns = (params: {
  pageNum: number;
  pageSize: number;
  materialId?: string;
  status?: string;
}): AxiosPromise<{ rows: TraceRepairRun[]; total: number }> => request({ url: '/erp/wm/trace/repair/runs', method: 'get', params });

/** 运行详情包含证据和服务端已审阅清单。 */
export const getRepairRun = (runId: string): AxiosPromise<TraceRepairRun> =>
  request({ url: `/erp/wm/trace/repair/runs/${encodeURIComponent(runId)}`, method: 'get' });

/** 只申请只读对账报告，没有 apply 或数据库修复调用。 */
export const auditMaterialTrace = (data: { materialId?: string; sourceDocType?: string; sourceDocId?: string }): AxiosPromise<TraceRepairRun> =>
  request({ url: '/erp/wm/trace/repair/audit', method: 'post', data });
