import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { PickingRuleQuery, PickingRuleVO, PickingRuleForm } from './types';

export const listPickingRule = (query: PickingRuleQuery): AxiosPromise<PickingRuleVO[]> => {
  return request({
    url: '/erp/wm/pickingrule/list',
    method: 'get',
    params: query
  });
};

export const getPickingRule = (ruleId: string | number): AxiosPromise<PickingRuleVO> => {
  return request({
    url: '/erp/wm/pickingrule/' + ruleId,
    method: 'get'
  });
};

export const addPickingRule = (data: PickingRuleForm) => {
  return request({
    url: '/erp/wm/pickingrule',
    method: 'post',
    data
  });
};

export const updatePickingRule = (data: PickingRuleForm) => {
  return request({
    url: '/erp/wm/pickingrule',
    method: 'put',
    data
  });
};

export const delPickingRule = (ruleId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/wm/pickingrule/' + ruleId,
    method: 'delete'
  });
};
