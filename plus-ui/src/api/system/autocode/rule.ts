import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 自动编码规则视图对象 */
export interface AutoCodeRuleVO {
  ruleId?: string | number;
  ruleCode?: string;
  ruleName?: string;
  ruleDesc?: string;
  enableFlag?: string;
  isPadded?: string;
  paddedChar?: string;
  paddedMethod?: string;
  maxLength?: number;
  remark?: string;
}

/** 自动编码规则查询条件 */
export interface AutoCodeRuleQuery extends PageQuery {
  ruleCode?: string;
  ruleName?: string;
}

/** 自动编码规则表单 */
export interface AutoCodeRuleForm {
  ruleId?: string | number;
  ruleCode?: string;
  ruleName?: string;
  ruleDesc?: string;
  enableFlag?: string;
  isPadded?: string;
  paddedChar?: string;
  paddedMethod?: string;
  maxLength?: number;
  remark?: string;
}

/** 生成编码（消耗流水号） */
export const genCode = (ruleCode: string | number): AxiosPromise<string> => {
  return request({
    url: '/system/autocode/get/' + ruleCode,
    method: 'get'
  });
};

// 预览编码（不落库，不消耗流水号）
export const previewCode = (ruleCode: string | number): AxiosPromise<string> => {
  return request({
    url: '/system/autocode/preview/' + ruleCode,
    method: 'get'
  });
};

export const listRule = (query?: AutoCodeRuleQuery): AxiosPromise<AutoCodeRuleVO[]> => {
  return request({
    url: '/system/autocode/rule/list',
    method: 'get',
    params: query
  });
};

export const getRule = (ruleId: string | number): AxiosPromise<AutoCodeRuleVO> => {
  return request({
    url: '/system/autocode/rule/' + ruleId,
    method: 'get'
  });
};

export const addRule = (data: AutoCodeRuleForm): AxiosPromise<void> => {
  return request({
    url: '/system/autocode/rule',
    method: 'post',
    data: data
  });
};

export const updateRule = (data: AutoCodeRuleForm): AxiosPromise<void> => {
  return request({
    url: '/system/autocode/rule',
    method: 'put',
    data: data
  });
};

export const delRule = (ruleId: string | number | Array<string | number>): AxiosPromise<void> => {
  return request({
    url: '/system/autocode/rule/' + ruleId,
    method: 'delete'
  });
};
