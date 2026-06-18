import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 安灯视图对象 */
export interface ProAndonVO {
  andonId: number;
  andonNo: string;
  mesWoId?: number;
  woNo?: string;
  cardId?: number;
  cardNo?: string;
  stepRecordId?: number;
  callType: string;
  callReason?: string;
  andonStatus: string;
  callerId?: number;
  callerName?: string;
  responderId?: number;
  responderName?: string;
  callTime?: string;
  respondTime?: string;
  resolveTime?: string;
  durationMinutes?: number;
  source?: string;
  remark?: string;
}

/** 安灯查询条件 */
export interface ProAndonQuery extends PageQuery {
  andonNo?: string;
  callType?: string;
  andonStatus?: string;
  source?: string;
  mesWoId?: number;
  cardId?: number;
}

/** 看板聚合统计 */
export interface AndonStats {
  activeCount: number;
  statusCount: Record<string, number>;
  typeCount: Record<string, number>;
}

/** 分页查询安灯列表 */
export const listAndon = (query?: ProAndonQuery): AxiosPromise<ProAndonVO[]> => request({ url: '/mes/pro/andon/list', method: 'get', params: query });

/** 获取安灯详情 */
export const getAndon = (andonId: string | number): AxiosPromise<ProAndonVO> => request({ url: `/mes/pro/andon/${andonId}`, method: 'get' });

/** 看板聚合统计 */
export const andonStats = (): AxiosPromise<AndonStats> => request({ url: '/mes/pro/andon/stats', method: 'get' });

/** 发起安灯呼叫 */
export const callAndon = (
  mesWoId: number | undefined,
  cardId: number | undefined,
  stepRecordId: number | undefined,
  callType: string,
  callReason?: string
) => request({ url: '/mes/pro/andon/call', method: 'post', params: { mesWoId, cardId, stepRecordId, callType, callReason } });

/** 响应安灯(CALLED -> RESPONDED) */
export const respondAndon = (andonId: string | number) => request({ url: `/mes/pro/andon/respond/${andonId}`, method: 'put' });

/** 解决安灯(RESPONDED -> RESOLVED) */
export const resolveAndon = (andonId: string | number, remark?: string) =>
  request({ url: `/mes/pro/andon/resolve/${andonId}`, method: 'put', params: { remark } });

/** 取消安灯(CALLED -> CANCELLED) */
export const cancelAndon = (andonId: string | number) => request({ url: `/mes/pro/andon/cancel/${andonId}`, method: 'put' });
