import { traceSave, executeTraceDocument } from '@/api/erp/wm/identity/document';
import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { MiscIssueQuery, MiscIssueVO, MiscIssueForm } from './types';

const BASE = '/erp/wm/miscissue';

export const listMiscIssue = (query: MiscIssueQuery): AxiosPromise<MiscIssueVO[]> => request({ url: BASE + '/list', method: 'get', params: query });

export const getMiscIssue = (id: string | number): AxiosPromise<MiscIssueVO> => request({ url: BASE + '/' + id, method: 'get' });

export const addMiscIssue = (data: MiscIssueForm) => request({ url: BASE, method: 'post', data: traceSave(data) });

export const updateMiscIssue = (data: MiscIssueForm) => request({ url: BASE, method: 'put', data: traceSave(data) });

export const delMiscIssue = (id: string | number | (string | number)[]) => request({ url: BASE + '/' + id, method: 'delete' });

export const exportMiscIssue = (query: MiscIssueQuery) => request({ url: BASE + '/export', method: 'post', params: query });

export const executeMiscIssue = (id: string | number, data?: Record<string, any>) =>
  executeTraceDocument({ url: BASE + '/execute/' + id, detailUrl: BASE + '/' + id, linesKey: 'wmMiscIssueLineList', quantityKey: 'quantity', data });
