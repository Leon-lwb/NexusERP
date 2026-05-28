import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { PutawayRuleQuery, PutawayRuleVO, PutawayRuleForm } from './types';

export const listPutawayRule = (query: PutawayRuleQuery): AxiosPromise<PutawayRuleVO[]> => {
  return request({
    url: '/erp/wm/putawayrule/list',
    method: 'get',
    params: query
  });
};

export const getPutawayRule = (ruleId: string | number): AxiosPromise<PutawayRuleVO> => {
  return request({
    url: '/erp/wm/putawayrule/' + ruleId, 
    method: 'get'
  });
};

export const addPutawayRule = (data: PutawayRuleForm) => {
  return request({
    url: '/erp/wm/putawayrule', 
    method: 'post',
    data
  });
};

export const updatePutawayRule = (data: PutawayRuleForm) => {
  return request({
    url: '/erp/wm/putawayrule', 
    method: 'put',
    data
  });
};

export const delPutawayRule = (ruleId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/wm/putawayrule/' + ruleId, 
    method: 'delete'
  });
};
