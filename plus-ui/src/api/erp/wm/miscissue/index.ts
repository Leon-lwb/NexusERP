import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { MiscIssueQuery, MiscIssueVO, MiscIssueForm } from './types';

const BASE = '/erp/wm/miscissue';

export const listMiscIssue = (query: MiscIssueQuery): AxiosPromise<{ rows: MiscIssueVO[]; total: number }> =>
  request({ url: BASE + '/list', method: 'get', params: query });

export const getMiscIssue = (id: string | number): AxiosPromise<MiscIssueVO> => request({ url: BASE + '/' + id, method: 'get' });

export const addMiscIssue = (data: MiscIssueForm) => request({ url: BASE, method: 'post', data });

export const updateMiscIssue = (data: MiscIssueForm) => request({ url: BASE, method: 'put', data });

export const delMiscIssue = (id: string | number | (string | number)[]) => request({ url: BASE + '/' + id, method: 'delete' });

export const exportMiscIssue = (query: MiscIssueQuery) => request({ url: BASE + '/export', method: 'post', params: query });

export const executeMiscIssue = (id: number) => request({ url: BASE + '/execute/' + id, method: 'put' });
