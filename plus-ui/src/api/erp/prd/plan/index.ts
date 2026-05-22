import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { PlanQuery, PlanVO, PlanForm } from './types';

export const listPlan = (query: PlanQuery): AxiosPromise<PlanVO[]> => {
  return request({
    url: '/erp/prd/plan/list',
    method: 'get',
    params: query
  });
};

export const getPlan = (planId: string | number): AxiosPromise<PlanVO> => {
  return request({
    url: '/erp/prd/plan/' + planId,
    method: 'get'
  });
};

export const addPlan = (data: PlanForm) => {
  return request({
    url: '/erp/prd/plan',
    method: 'post',
    data
  });
};

export const updatePlan = (data: PlanForm) => {
  return request({
    url: '/erp/prd/plan',
    method: 'put',
    data
  });
};

export const delPlan = (planId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/prd/plan/' + planId,
    method: 'delete'
  });
};

export const updatePlanVersion = (data: PlanForm) => {
  return request({
    url: '/erp/prd/plan/updateVersion',
    method: 'put',
    data
  });
};

export const calculateBom = (planId: string | number): AxiosPromise<PlanVO> => {
  return request({
    url: '/erp/prd/plan/calculateBom/' + planId,
    method: 'get'
  });
};

export const generateMaterialRequirementsWithDetails = (planId: string | number) => {
  return request({
    url: '/erp/prd/plan/generateMaterialRequirementsWithDetails/' + planId,
    method: 'post'
  });
};

export const generateWorkOrders = (planId: string | number, requirementIds: (string | number)[]) => {
  return request({
    url: '/erp/prd/plan/generateWorkOrders/' + planId,
    method: 'post',
    data: requirementIds
  });
};

export const listDetailsByPlanNo = (planNo: string): AxiosPromise => {
  return request({
    url: '/erp/prd/requirement/listDetailsByPlanNo/' + planNo,
    method: 'get'
  });
};
