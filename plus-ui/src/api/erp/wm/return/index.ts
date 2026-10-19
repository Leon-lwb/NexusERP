import { traceSave } from '@/api/erp/wm/identity/document';
import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ReturnQuery, ReturnVO, ReturnForm } from './types';

const BASE = '/erp/wm/return';

/** 退料单列表 */
export const listReturn = (query: ReturnQuery): AxiosPromise<ReturnVO[]> => request({ url: BASE + '/list', method: 'get', params: query });

/** 退料单详情 (头+行) */
export const getReturn = (id: string | number): AxiosPromise<ReturnVO> => request({ url: BASE + '/' + id, method: 'get' });

/** 新建退料单 (状态 DRAFT, 待 PDA 核销) */
export const addReturn = (data: ReturnForm) => request({ url: BASE, method: 'post', data: traceSave(data) });
