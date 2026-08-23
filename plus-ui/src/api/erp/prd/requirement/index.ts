import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { RequirementQuery, RequirementVO, RequirementForm } from './types';

export const listRequirement = (query: RequirementQuery): AxiosPromise<RequirementVO[]> => {
  return request({
    url: '/erp/prd/requirement/list',
    method: 'get',
    params: query
  });
};

export const getRequirement = (requirementId: string | number): AxiosPromise<RequirementVO> => {
  return request({
    url: '/erp/prd/requirement/' + requirementId,
    method: 'get'
  });
};

export const addRequirement = (data: RequirementForm) => {
  return request({
    url: '/erp/prd/requirement',
    method: 'post',
    data
  });
};

export const updateRequirement = (data: RequirementForm) => {
  return request({
    url: '/erp/prd/requirement',
    method: 'put',
    data
  });
};

export const delRequirement = (requirementId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/prd/requirement/' + requirementId,
    method: 'delete'
  });
};

export const listTreeByPlanNo = (planNo: string) => {
  return request({
    url: '/erp/prd/requirement/treeByPlanNo/' + planNo,
    method: 'get'
  });
};

export const importBomDetails = (bomId: string | number, totalRequiredQuantity: number): AxiosPromise<any> => {
  return request({
    url: '/erp/prd/requirement/importBomDetails',
    method: 'get',
    params: { bomId, totalRequiredQuantity }
  });
};
