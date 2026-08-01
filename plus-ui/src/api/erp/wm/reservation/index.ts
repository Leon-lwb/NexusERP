import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ReservationQuery, ReservationTxVO, ReservationSummaryVO, ReserveForm } from './types';

/** 分页查询预留明细（RESERVE/UNRESERVE 流水） */
export function listReservation(query: ReservationQuery): AxiosPromise<ReservationTxVO[]> {
  return request({ url: '/erp/wm/reservation/list', method: 'get', params: query });
}

/** 分页查询预留汇总（在库/已预留/可用数量） */
export function listReservationSummary(query: ReservationQuery): AxiosPromise<ReservationSummaryVO[]> {
  return request({ url: '/erp/wm/reservation/summary', method: 'get', params: query });
}

/** 手动预留库存 */
export function reserveStock(data: ReserveForm) {
  return request({ url: '/erp/wm/reservation/reserve', method: 'post', data });
}

/** 释放预留 */
export function unreserveStock(data: ReserveForm) {
  return request({ url: '/erp/wm/reservation/unreserve', method: 'post', data });
}

/** 检查可用库存 */
export function checkAvailable(materialId: number | string, warehouseId?: number | string): AxiosPromise<number> {
  return request({
    url: '/erp/wm/reservation/available/' + materialId,
    method: 'get',
    params: { warehouseId }
  });
}
