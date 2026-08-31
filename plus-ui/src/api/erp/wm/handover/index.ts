import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { HandoverQuery, HandoverVO, HandoverForm } from './types';

const BASE = '/erp/wm/handover';

/** 交接单列表 */
export const listHandover = (query: HandoverQuery): AxiosPromise<HandoverVO[]> => request({ url: BASE + '/list', method: 'get', params: query });

/** 交接单详情 (头+行) */
export const getHandover = (id: string | number): AxiosPromise<HandoverVO> => request({ url: BASE + '/' + id, method: 'get' });

/** 新建交接单 (状态 DRAFT, 待 PDA 核销+签收) */
export const addHandover = (data: HandoverForm) => request({ url: BASE, method: 'post', data });
