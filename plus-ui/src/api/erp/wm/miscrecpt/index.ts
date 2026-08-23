import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { MiscRecptQuery, MiscRecptVO, MiscRecptForm } from './types';

const BASE = '/erp/wm/miscrecpt';

export const listMiscRecpt = (query: MiscRecptQuery): AxiosPromise<MiscRecptVO[]> => request({ url: BASE + '/list', method: 'get', params: query });

export const getMiscRecpt = (id: string | number): AxiosPromise<MiscRecptVO> => request({ url: BASE + '/' + id, method: 'get' });

export const addMiscRecpt = (data: MiscRecptForm) => request({ url: BASE, method: 'post', data });

export const updateMiscRecpt = (data: MiscRecptForm) => request({ url: BASE, method: 'put', data });

export const delMiscRecpt = (id: string | number | (string | number)[]) => request({ url: BASE + '/' + id, method: 'delete' });

export const exportMiscRecpt = (query: MiscRecptQuery) => request({ url: BASE + '/export', method: 'post', params: query });

export const executeMiscRecpt = (id: number) => request({ url: BASE + '/execute/' + id, method: 'put' });
