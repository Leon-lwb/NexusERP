import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { WmTraceResultVO, WmTraceQuery } from './types';

/** 正向追溯（原材料 → 成品） */
export function traceForward(query: WmTraceQuery): AxiosPromise<WmTraceResultVO> {
  return request({
    url: '/erp/wm/trace/forward',
    method: 'get',
    params: query
  });
}

/** 反向追溯（成品 → 原材料） */
export function traceBackward(query: WmTraceQuery): AxiosPromise<WmTraceResultVO> {
  return request({
    url: '/erp/wm/trace/backward',
    method: 'get',
    params: query
  });
}

/** 首次不指定模块，服务端只返回当前用户获准查看的 ERP/MES 分部。 */
export function traceIdentity(
  identityId: string,
  page?: import('./types').IdentityTracePageQuery
): AxiosPromise<import('./types').IdentityTraceResult> {
  return request({ url: `/erp/wm/trace/identity/${encodeURIComponent(identityId)}`, method: 'get', params: { limit: 500, ...page } });
}

/** 召回身份、工单范围与受影响批次使用独立游标，范围与数值文本原样保留。 */
export function recallBatch(batchId: string, page?: import('./types').BatchRecallPageQuery): AxiosPromise<import('./types').BatchRecallResult> {
  return request({ url: '/erp/wm/trace/recall', method: 'get', params: { batchId, afterIdentity: '0', limit: 500, depth: 10, ...page } });
}

/** 批次自身库存、实际流转去向、来源与MES lot独立分页；只请求已授权模块分节。 */
export function traceBatch(batchId: string, page?: import('./types').BatchTracePageQuery): AxiosPromise<import('./types').BatchTraceResult> {
  return request({ url: `/erp/wm/trace/batch/${encodeURIComponent(batchId)}`, method: 'get', params: { limit: 500, ...page } });
}

/** 组件身份去向沿原真实身份查询，批次粗粒度影响不当作逐件证明。 */
export function whereUsedIdentity(
  identityId: string,
  page?: import('./types').BatchRecallPageQuery
): AxiosPromise<import('./types').IdentityWhereUsedResult> {
  return request({
    url: `/erp/wm/trace/where-used/${encodeURIComponent(identityId)}`,
    method: 'get',
    params: { afterIdentity: '0', limit: 500, depth: 10, ...page }
  });
}
