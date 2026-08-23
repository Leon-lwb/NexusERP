import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 工序流程指引视图对象 */
export interface ProStepGuideVO {
  guideId: number;
  processId: number;
  processCode?: string;
  processName?: string;
  guideType: string;
  guideTitle?: string;
  guideContent?: string;
  sopId?: number;
  sopName?: string;
  sortOrder?: number;
  enabled: string;
}

/** 工序流程指引查询条件 */
export interface ProStepGuideQuery extends PageQuery {
  processId?: number;
  processName?: string;
  guideType?: string;
  enabled?: string;
}

/** 工序流程指引表单 */
export interface ProStepGuideForm {
  guideId?: number;
  processId: number;
  processCode?: string;
  processName?: string;
  sopName?: string;
  guideType: string;
  guideTitle?: string;
  guideContent?: string;
  sopId?: number;
  sortOrder?: number;
  enabled?: string;
}

/** 分页查询工序流程指引列表 */
export const listStepGuide = (query?: ProStepGuideQuery): AxiosPromise<ProStepGuideVO[]> =>
  request({ url: '/mes/pro/step-guide/list', method: 'get', params: query });

/** 获取工序流程指引详情 */
export const getStepGuide = (guideId: number | string): AxiosPromise<ProStepGuideVO> =>
  request({ url: `/mes/pro/step-guide/${guideId}`, method: 'get' });

/** 新增工序流程指引 */
export const addStepGuide = (data: ProStepGuideForm) => request({ url: '/mes/pro/step-guide', method: 'post', data });

/** 修改工序流程指引 */
export const updateStepGuide = (data: ProStepGuideForm) => request({ url: '/mes/pro/step-guide', method: 'put', data });

/** 批量删除工序流程指引 */
export const delStepGuide = (guideIds: number | number[]) => request({ url: `/mes/pro/step-guide/${guideIds}`, method: 'delete' });

/** 导出工序流程指引 */
export const exportStepGuide = (query?: ProStepGuideQuery): Promise<Blob> =>
  request({ url: '/mes/pro/step-guide/export', method: 'post', params: query, responseType: 'blob' }) as unknown as Promise<Blob>;
