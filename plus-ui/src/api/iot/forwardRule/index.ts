import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ForwardLogQuery, ForwardLogVO, ForwardRuleForm, ForwardRuleQuery, ForwardRuleVO } from './types';

/** 查询转发规则列表（不分页；权限码 iot:forwardRule:list） */
export const listForwardRule = (query?: ForwardRuleQuery): AxiosPromise<ForwardRuleVO[]> => {
  return request({ url: '/iot/forwardRule/list', method: 'get', params: query });
};

/** 规则详情（权限码 iot:forwardRule:query） */
export const getForwardRule = (ruleId: string | number): AxiosPromise<ForwardRuleVO> => {
  return request({ url: '/iot/forwardRule/' + ruleId, method: 'get' });
};

/** 新增规则（保存时后端即做 SSRF 校验；权限码 iot:forwardRule:add） */
export const addForwardRule = (data: Partial<ForwardRuleForm>) => {
  return request({ url: '/iot/forwardRule', method: 'post', data });
};

/** 修改规则（权限码 iot:forwardRule:edit） */
export const updateForwardRule = (data: Partial<ForwardRuleForm>) => {
  return request({ url: '/iot/forwardRule', method: 'put', data });
};

/** 批量删除规则（投递留痕保留；权限码 iot:forwardRule:remove） */
export const delForwardRule = (ruleIds: string | number | Array<string | number>) => {
  return request({ url: '/iot/forwardRule/' + ruleIds, method: 'delete' });
};

/** 分页查询投递留痕（权限码 iot:forwardRule:log） */
export const listForwardLog = (query: ForwardLogQuery): AxiosPromise<ForwardLogVO[]> => {
  return request({ url: '/iot/forwardRule/log/list', method: 'get', params: query });
};
