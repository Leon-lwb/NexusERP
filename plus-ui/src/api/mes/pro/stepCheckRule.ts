import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 工序校验规则视图对象 */
export interface ProStepCheckRuleVO {
  ruleId: number;
  processId: number;
  processCode?: string;
  processName?: string;
  checkType: string;
  checkStage: string;
  enabled: string;
  errorMsg?: string;
  sortOrder?: number;
}

/** 工序校验规则查询条件 */
export interface ProStepCheckRuleQuery extends PageQuery {
  processId?: number;
  processName?: string;
  checkType?: string;
  checkStage?: string;
  enabled?: string;
}

/** 工序校验规则表单 */
export interface ProStepCheckRuleForm {
  ruleId?: number;
  processId: number;
  processCode?: string;
  processName?: string;
  checkType: string;
  checkStage: string;
  enabled?: string;
  errorMsg?: string;
  sortOrder?: number;
}

/** 分页查询工序校验规则列表 */
export const listStepCheckRule = (query?: ProStepCheckRuleQuery): AxiosPromise<ProStepCheckRuleVO[]> =>
  request({ url: '/mes/pro/step-check-rule/list', method: 'get', params: query });

/** 获取工序校验规则详情 */
export const getStepCheckRule = (ruleId: number | string): AxiosPromise<ProStepCheckRuleVO> =>
  request({ url: `/mes/pro/step-check-rule/${ruleId}`, method: 'get' });

/** 新增工序校验规则 */
export const addStepCheckRule = (data: ProStepCheckRuleForm) => request({ url: '/mes/pro/step-check-rule', method: 'post', data });

/** 修改工序校验规则 */
export const updateStepCheckRule = (data: ProStepCheckRuleForm) => request({ url: '/mes/pro/step-check-rule', method: 'put', data });

/** 批量删除工序校验规则 */
export const delStepCheckRule = (ruleIds: number | number[]) => request({ url: `/mes/pro/step-check-rule/${ruleIds}`, method: 'delete' });

/** 导出工序校验规则 */
export const exportStepCheckRule = (query?: ProStepCheckRuleQuery): Promise<Blob> =>
  request({ url: '/mes/pro/step-check-rule/export', method: 'post', params: query, responseType: 'blob' }) as unknown as Promise<Blob>;
