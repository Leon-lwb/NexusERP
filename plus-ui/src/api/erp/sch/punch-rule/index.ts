import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { PunchRuleQuery, PunchRuleVO, PunchRuleForm } from './types';

export const listPunchRule = (query: PunchRuleQuery): AxiosPromise<PunchRuleVO[]> => {
  return request({
    url: '/erp/sch/punchRule/list',
    method: 'get',
    params: query
  });
};

export const getPunchRule = (ruleId: string | number): AxiosPromise<PunchRuleVO> => {
  return request({
    url: '/erp/sch/punchRule/' + ruleId,
    method: 'get'
  });
};

export const addPunchRule = (data: PunchRuleForm) => {
  return request({
    url: '/erp/sch/punchRule',
    method: 'post',
    data
  });
};

export const updatePunchRule = (data: PunchRuleForm) => {
  return request({
    url: '/erp/sch/punchRule',
    method: 'put',
    data
  });
};

export const delPunchRule = (ruleId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/sch/punchRule/' + ruleId,
    method: 'delete'
  });
};
