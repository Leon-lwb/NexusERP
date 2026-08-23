import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import type { SpcDataPointVO } from './control-chart';

/** 数据点查询条件 */
export interface SpcDataPointQuery extends PageQuery {
  chartId?: number;
  sampleGroup?: number;
  isOoc?: string;
}

/** 分页查询数据点 */
export const listDataPoint = (query?: SpcDataPointQuery): AxiosPromise<SpcDataPointVO[]> =>
  request({ url: '/mes/spc/data-point/list', method: 'get', params: query });

/** 按控制图查询全部数据点(供绘图) */
export const listDataPointByChart = (chartId: string | number): AxiosPromise<SpcDataPointVO[]> =>
  request({ url: `/mes/spc/data-point/by-chart/${chartId}`, method: 'get' });
