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
  /** 次级图(极差图R/标准差图S/移动极差图MR)上控制限 */
  rUcl?: number;
  /** 次级图中心线(R̄/S̄/MR̄) */
  rCl?: number;
  /** 次级图下控制限 */
  rLcl?: number;
  usl?: number;
  lsl?: number;
  cp?: number;
  cpk?: number;
  cpkGrade?: string;
  /** 过程性能指数Pp(长期标准差) */
  pp?: number;
  /** 过程性能指数Ppk(长期标准差) */
  ppk?: number;
  /** 过程能力样本量不足(仅 analyze 返回, 不落库) */
  capabilityInsufficient?: boolean;
  /** 过程能力不足原因(仅 analyze 返回, 不落库) */
  capabilityInsufficientReason?: string;
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
  /** 组内离散度(R/S/MR), 次级图取值 */
  subValue?: number;
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
  /** 次级图(极差图R/标准差图S/移动极差图MR)控制限 */
  rUcl?: number;
  rCl?: number;
  rLcl?: number;
  cp?: number;
  cpk?: number;
  cpkGrade?: string;
  /** 过程性能指数Pp/Ppk(长期标准差) */
  pp?: number;
  ppk?: number;
  /** 过程能力样本量不足(子组<25 或样本<50 或未维护规格限) */
  capabilityInsufficient?: boolean;
  /** 过程能力不足原因 */
  capabilityInsufficientReason?: string;
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

/** 分析控制图(计算控制限 + OOC判定; 后端 MES-SPC-03 已改 POST, GET 会 405) */
export const analyzeControlChart = (chartId: string | number): AxiosPromise<SpcAnalyzeResult> =>
  request({ url: `/mes/spc/control-chart/analyze/${chartId}`, method: 'post' });
