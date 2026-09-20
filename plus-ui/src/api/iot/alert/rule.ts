import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { AlertRuleQuery, AlertRuleVO, AlertRuleForm } from './types';

/** 分页查询告警规则（权限码 iot:alertRule:list） */
export const listAlertRule = (query: AlertRuleQuery): AxiosPromise<AlertRuleVO[]> => {
  return request({ url: '/iot/alertRule/list', method: 'get', params: query });
};

/** 查询规则列表（不分页，供下拉/本地匹配；权限码 iot:alertRule:list） */
export const optionAlertRule = (query?: AlertRuleQuery): AxiosPromise<AlertRuleVO[]> => {
  return request({ url: '/iot/alertRule/optionList', method: 'get', params: query });
};

/** 规则详情（权限码 iot:alertRule:query） */
export const getAlertRule = (ruleId: string | number): AxiosPromise<AlertRuleVO> => {
  return request({ url: '/iot/alertRule/' + ruleId, method: 'get' });
};

/** 新增规则（权限码 iot:alertRule:add） */
export const addAlertRule = (data: Partial<AlertRuleForm>) => {
  return request({ url: '/iot/alertRule', method: 'post', data });
};

/** 修改规则（权限码 iot:alertRule:edit） */
export const updateAlertRule = (data: Partial<AlertRuleForm>) => {
  return request({ url: '/iot/alertRule', method: 'put', data });
};

/** 启用/停用规则（权限码 iot:alertRule:changeStatus） */
export const changeAlertRuleEnabled = (ruleId: string | number, enabled: string) => {
  return request({ url: '/iot/alertRule/changeEnabled', method: 'put', params: { ruleId, enabled } });
};

/** 批量删除规则（权限码 iot:alertRule:remove） */
export const delAlertRule = (ruleIds: string | number | Array<string | number>) => {
  return request({ url: '/iot/alertRule/' + ruleIds, method: 'delete' });
};
