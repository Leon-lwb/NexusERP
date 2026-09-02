import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 控制图视图对象 */
export interface SpcControlChartVO {
  chartId: number;
  chartName: string;
  chartType: string;
  pointId?: number;
  materialId?: number;
  processId?: number;
  ucl?: number;
  cl?: number;
  lcl?: number;
  usl?: number;
  lsl?: number;
  cp?: number;
  cpk?: number;
  cpkGrade?: string;
  sampleSize: number;
  status: string;
  remark?: string;
}

/** 控制图查询条件 */
export interface SpcControlChartQuery extends PageQuery {
  chartName?: string;
  chartType?: string;
  status?: string;
  pointId?: number;
}

/** 控制图表单 */
export interface SpcControlChartForm {
  chartId?: number;
  chartName: string;
  chartType: string;
  pointId?: number;
  materialId?: number;
  processId?: number;
  usl?: number;
  lsl?: number;
  sampleSize?: number;
  status?: string;
  remark?: string;
}

/** 分析结果数据点 */
export interface SpcDataPointVO {
  dataId: number;
  chartId: number;
  sampleGroup: number;
  sampleIndex?: number;
  value: number;
  isOoc: string;
  oocRule?: string;
  collectTime?: string;
}

/** 分析结果 */
export interface SpcAnalyzeResult {
  chart: SpcControlChartVO;
  points: SpcDataPointVO[];
  oocCount: number;
  ucl: number;
  cl: number;
  lcl: number;
  cp?: number;
  cpk?: number;
  cpkGrade?: string;
}

/** 分页查询控制图 */
export const listControlChart = (query?: SpcControlChartQuery): AxiosPromise<SpcControlChartVO[]> =>
  request({ url: '/mes/spc/control-chart/list', method: 'get', params: query });

/** 获取控制图详情 */
export const getControlChart = (chartId: string | number): AxiosPromise<SpcControlChartVO> =>
  request({ url: `/mes/spc/control-chart/${chartId}`, method: 'get' });

/** 新增控制图 */
export const addControlChart = (data: SpcControlChartForm): AxiosPromise<SpcControlChartVO> =>
  request({ url: '/mes/spc/control-chart', method: 'post', data });

/** 编辑控制图 */
export const updateControlChart = (data: SpcControlChartForm): AxiosPromise<SpcControlChartVO> =>
  request({ url: '/mes/spc/control-chart', method: 'put', data });

/** 删除控制图(支持单个 id 或批量) */
export const delControlChart = (chartIds: Array<string | number> | string | number): AxiosPromise<void> =>
  request({ url: `/mes/spc/control-chart/${chartIds}`, method: 'delete' });

/** 分析控制图(计算控制限 + OOC判定) */
export const analyzeControlChart = (chartId: string | number): AxiosPromise<SpcAnalyzeResult> =>
  request({ url: `/mes/spc/control-chart/analyze/${chartId}`, method: 'get' });
