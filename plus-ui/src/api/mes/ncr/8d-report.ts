import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 8D报告视图对象 */
export interface Ncr8dReportVO {
  reportId: number;
  ncrId: number;
  d1Team?: string;
  d2Problem?: string;
  d3Interim?: string;
  d4RootCause?: string;
  d5Permanent?: string;
  d6Implement?: string;
  d7Prevent?: string;
  d8Recognize?: string;
  status?: string;
  completedBy?: number;
  completedByName?: string;
  completedTime?: string;
}

/** 8D报告表单 */
export interface Ncr8dReportForm {
  reportId?: number;
  ncrId?: number;
  d1Team?: string;
  d2Problem?: string;
  d3Interim?: string;
  d4RootCause?: string;
  d5Permanent?: string;
  d6Implement?: string;
  d7Prevent?: string;
  d8Recognize?: string;
}

/** 根据NCR ID获取8D报告 */
export const get8dByNcr = (ncrId: number | string): AxiosPromise<Ncr8dReportVO> => request({ url: `/mes/ncr/8d/${ncrId}`, method: 'get' });

/** 更新8D报告 */
export const update8d = (data: Ncr8dReportForm) => request({ url: '/mes/ncr/8d', method: 'put', data });

/** 完成8D报告 */
export const complete8d = (reportId: number | string) => request({ url: `/mes/ncr/8d/complete/${reportId}`, method: 'put' });
