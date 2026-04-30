import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { SchPlanQuery, SchPlanVO, SchPlanForm } from './types';

export const listPlan = (query: SchPlanQuery): AxiosPromise<SchPlanVO[]> => {
  return request({
    url: '/erp/sch/plan/list',
    method: 'get',
    params: query
  });
};

export const getPlan = (planId: string | number): AxiosPromise<SchPlanVO> => {
  return request({
    url: '/erp/sch/plan/' + planId,
    method: 'get'
  });
};

export const addPlan = (data: SchPlanForm) => {
  return request({
    url: '/erp/sch/plan',
    method: 'post',
    data
  });
};

export const updatePlan = (data: SchPlanForm) => {
  return request({
    url: '/erp/sch/plan',
    method: 'put',
    data
  });
};

export const delPlan = (planId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/sch/plan/' + planId,
    method: 'delete'
  });
};
