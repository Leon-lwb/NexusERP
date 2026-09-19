import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ReturnQuery, ReturnVO, ReturnForm } from './types';

const BASE = '/erp/so/return';

export const listReturn = (query: ReturnQuery): AxiosPromise<ReturnVO[]> => request({ url: BASE + '/list', method: 'get', params: query });

export const getReturn = (id: string | number): AxiosPromise<ReturnVO> => request({ url: BASE + '/' + id, method: 'get' });

export const addReturn = (data: ReturnForm) => request({ url: BASE, method: 'post', data });

export const updateReturn = (data: ReturnForm) => request({ url: BASE, method: 'put', data });

export const delReturn = (id: string | number | (string | number)[]) => request({ url: BASE + '/' + id, method: 'delete' });

export const exportReturn = (query: ReturnQuery) => request({ url: BASE + '/export', method: 'post', params: query });

export const submitReturnFlow = (id: string | number, selectFlowId?: string) =>
  request({ url: BASE + '/' + id + '/submit', method: 'post', params: { selectFlowId } });

export const getReturnProcId = (id: string | number) => request({ url: BASE + '/' + id + '/proc', method: 'get' });

/**
 * 确认退货单（DRAFT → CONFIRMED）。
 * <p>后端 `PUT /erp/so/return/confirm/{id}` 早已存在，但前端 API 层一直没有对应函数
 * —— 导致「确认」与「入库」两个动作在界面上完全不可达，退货闭环断在 UI 上（T3.3 修复）。</p>
 */
export const confirmReturn = (id: string | number) => request({ url: BASE + '/confirm/' + id, method: 'put' });

/**
 * 执行退货入库（CONFIRMED → RECEIVED）。
 * <p>会加库存、生成库存流水，并把退货数量累加回订单行 `returned_qty`
 * （超退守卫：已退货 + 本次 <= 已发货，超出则整体回滚）。</p>
 */
export const executeReturn = (id: string | number) => request({ url: BASE + '/execute/' + id, method: 'put' });
