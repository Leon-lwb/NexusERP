import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { PrdSubcontractVO, PrdSubcontractQuery, PrdSubcontractForm } from './types';

export function listSubcontract(query: PrdSubcontractQuery): AxiosPromise<PrdSubcontractVO[]> {
  return request({ url: '/erp/prd/subcontract/list', method: 'get', params: query });
}

export function getSubcontract(subcontractId: number | string): AxiosPromise<PrdSubcontractVO> {
  return request({ url: '/erp/prd/subcontract/' + subcontractId, method: 'get' });
}

export function addSubcontract(data: PrdSubcontractForm) {
  return request({ url: '/erp/prd/subcontract', method: 'post', data });
}

export function updateSubcontract(data: PrdSubcontractForm) {
  return request({ url: '/erp/prd/subcontract', method: 'put', data });
}

export function delSubcontract(subcontractIds: number | string | Array<number | string>) {
  return request({ url: '/erp/prd/subcontract/' + subcontractIds, method: 'delete' });
}

export function releaseSubcontract(subcontractId: number) {
  return request({ url: '/erp/prd/subcontract/release/' + subcontractId, method: 'put' });
}

export function startSubcontract(subcontractId: number) {
  return request({ url: '/erp/prd/subcontract/start/' + subcontractId, method: 'put' });
}

export function completeSubcontract(subcontractId: number) {
  return request({ url: '/erp/prd/subcontract/complete/' + subcontractId, method: 'put' });
}

export function closeSubcontract(subcontractId: number) {
  return request({ url: '/erp/prd/subcontract/close/' + subcontractId, method: 'put' });
}

const BASE = '/erp/prd/subcontract';

export const issueSubcontractMaterial = (subcontractId: number) => request({ url: BASE + '/issue/' + subcontractId, method: 'put' });

export const receiveSubcontractMaterial = (subcontractId: number) => request({ url: BASE + '/receive/' + subcontractId, method: 'put' });

export function submitApproval(id: string | number) {
  return request({ url: '/erp/prd/subcontract/submit/' + id, method: 'post' });
}

export function getOperationLogs(id: string | number) {
  return request({ url: '/erp/prd/subcontract/logs/' + id, method: 'get' });
}

// ==================== PCBA 委外闭环 ====================

/** 选料带出资料(当前生效BOM+行/委外工序/物料级工程文件库) */
export const getMaterialInfo = (materialId: number | string): AxiosPromise<any> =>
  request({ url: BASE + '/material-info/' + materialId, method: 'get' });

/** 订单 BOM 快照行 */
export const getBomLines = (subcontractId: number | string): AxiosPromise<any[]> =>
  request({ url: BASE + '/bom-lines/' + subcontractId, method: 'get' });

/** 生成/重建订单 BOM 快照(仅草稿) */
export const generateSnapshot = (subcontractId: number | string) => request({ url: BASE + '/generate-snapshot/' + subcontractId, method: 'put' });

/** 订单报价列表 */
export const listQuotes = (subcontractId: number | string): AxiosPromise<any[]> => request({ url: BASE + '/quotes/' + subcontractId, method: 'get' });

/** 保存报价 */
export const saveQuote = (data: any) => request({ url: BASE + '/quote', method: 'post', data });

/** 删除报价 */
export const delQuotes = (quoteIds: (number | string)[]) => request({ url: BASE + '/quote/' + quoteIds.join(','), method: 'delete' });

/** 选定供应商(报价回写订单) */
export const selectQuote = (quoteId: number | string) => request({ url: BASE + '/quote/select/' + quoteId, method: 'put' });

/** 工程文件列表(scId=0 查物料级库) */
export const listDocs = (params: { materialId?: number | string; subcontractId?: number | string }): AxiosPromise<any[]> =>
  request({ url: BASE + '/docs', method: 'get', params });

/** 保存工程文件(先 OSS 上传取 fileOssId) */
export const saveDoc = (data: any) => request({ url: BASE + '/doc', method: 'post', data });

/** 删除工程文件 */
export const delDocs = (docIds: (number | string)[]) => request({ url: BASE + '/doc/' + docIds.join(','), method: 'delete' });

// ==================== 委外发料单 / 收货单 ====================

const ISSUE_BASE = '/erp/wm/sc-issue';
const RECEIVE_BASE = '/erp/wm/sc-receive';

/** 发料单列表(按订单) */
export const listIssues = (subcontractId: number | string): AxiosPromise<any[]> =>
  request({ url: ISSUE_BASE + '/list/' + subcontractId, method: 'get' });

/** 发料单详情(含行) */
export const getIssue = (issueId: number | string): AxiosPromise<any> => request({ url: ISSUE_BASE + '/' + issueId, method: 'get' });

/** 备料建议(自备料行 × 可用库存 FEFO/FIFO) */
export const suggestAllocations = (subcontractId: number | string): AxiosPromise<any[]> =>
  request({ url: ISSUE_BASE + '/suggest/' + subcontractId, method: 'get' });

/** 创建发料单 */
export const createIssue = (data: any) => request({ url: ISSUE_BASE, method: 'post', data });

/** 删除发料单(草稿) */
export const delIssue = (issueId: number | string) => request({ url: ISSUE_BASE + '/' + issueId, method: 'delete' });

/** 发料过账(扣库存, 全部发齐自动推进订单+SN MQ) */
export const postIssue = (issueId: number | string) => request({ url: ISSUE_BASE + '/post/' + issueId, method: 'put' });

/** 收货单列表(按订单) */
export const listReceives = (subcontractId: number | string): AxiosPromise<any[]> =>
  request({ url: RECEIVE_BASE + '/list/' + subcontractId, method: 'get' });

/** 收货单详情 */
export const getReceive = (receiveId: number | string): AxiosPromise<any> => request({ url: RECEIVE_BASE + '/' + receiveId, method: 'get' });

/** 创建收货单 */
export const createReceive = (data: any) => request({ url: RECEIVE_BASE, method: 'post', data });

/** 删除收货单(草稿) */
export const delReceive = (receiveId: number | string) => request({ url: RECEIVE_BASE + '/' + receiveId, method: 'delete' });

/** 收货过账(自动建批次入库, actualQty 累加, 达量自动完工) */
export const postReceive = (receiveId: number | string) => request({ url: RECEIVE_BASE + '/post/' + receiveId, method: 'put' });

/** MES SN 列表(按委外单号过滤) */
export const listSubcontractSns = (subcontractNo: string): AxiosPromise<any[]> =>
  request({ url: '/mes/pro/sn/list', method: 'get', params: { subcontractNo, pageNum: 1, pageSize: 500 } });
