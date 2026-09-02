import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { EqHealthRecordVO, EqHealthDetail } from './types';

/**
 * 分页查询设备最新健康评分(最差优先)
 */
export function listHealth(query: { equipmentName?: string; grade?: string } & PageQuery): AxiosPromise<EqHealthRecordVO[]> {
  return request({ url: '/mes/eq/health/list', method: 'get', params: query });
}

/**
 * 查询设备评分历史趋势
 */
export function healthTrend(equipmentId: string | number, limitDays?: number): AxiosPromise<EqHealthRecordVO[]> {
  return request({ url: `/mes/eq/health/trend/${equipmentId}`, method: 'get', params: { limitDays } });
}

/**
 * 手动重新评估全部设备健康
 */
export function evaluateHealth(): AxiosPromise<number> {
  return request({ url: '/mes/eq/health/evaluate', method: 'post' });
}

/**
 * 解析明细 JSON(后端 detailJson 字段)
 */
export function parseDetail(detailJson?: string): EqHealthDetail[] {
  if (!detailJson) return [];
  try {
    return JSON.parse(detailJson) as EqHealthDetail[];
  } catch {
    return [];
  }
}
