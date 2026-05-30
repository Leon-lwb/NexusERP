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

export const updateSerialNumberRule = (data: SerialNumberRuleForm) =>
  request({
    url: BASE,
    method: 'put',
    data
  });

export const delSerialNumberRule = (ruleId: string | number | (string | number)[]) =>
  request({
    url: BASE + '/' + ruleId,
    method: 'delete'
  });

export const generateSerialNumber = (ruleCode: string): AxiosPromise<string> =>
  request({
    url: BASE + '/generate/' + ruleCode,
    method: 'post'
  });
