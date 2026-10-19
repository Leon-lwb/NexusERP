import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { SerialNumberRuleQuery, SerialNumberRuleVO, SerialNumberRuleForm } from './types';

const BASE = '/erp/bd/serial-number-rule';

export const listSerialNumberRule = (query: SerialNumberRuleQuery): AxiosPromise<SerialNumberRuleVO[]> =>
  request({
    url: BASE + '/list',
    method: 'get',
    params: query
  });

export const getSerialNumberRule = (ruleId: string | number): AxiosPromise<SerialNumberRuleVO> =>
  request({
    url: BASE + '/' + ruleId,
    method: 'get'
  });

export const addSerialNumberRule = (data: SerialNumberRuleForm) =>
  request({
    url: BASE,
    method: 'post',
    data
  });

/** 编辑必须携带服务端原版本，不在客户端生成、递增或转成 Number。 */
export const updateSerialNumberRule = (data: SerialNumberRuleForm) => {
  if (typeof data.ruleVersion !== 'string' || !data.ruleVersion) return Promise.reject(new Error('缺少规则原版本，请重新加载后编辑'));
  return request({ url: BASE, method: 'put', data });
};

export const delSerialNumberRule = (ruleId: string | number | (string | number)[]) =>
  request({
    url: BASE + '/' + ruleId,
    method: 'delete'
  });

/** 仅非正式 SN 规则继续使用旧生成接口；正式实物请走 reserve。 */
export const generateSerialNumber = (ruleCode: string, ruleType?: string): AxiosPromise<string> => {
  if (ruleType === 'PRODUCT_SN') return Promise.reject(new Error('正式 SN 请使用 /erp/wm/sn/reserve 登记'));
  return request({ url: BASE + '/generate/' + ruleCode, method: 'post' });
};
