import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ProductionIssueQuery, ProductionIssueVO, ProductionIssueForm } from './types';

export const listProductionIssue = (query: ProductionIssueQuery): AxiosPromise<ProductionIssueVO[]> => {
  return request({
    url: '/erp/wm/productionissue/list',
    method: 'get',
    params: query
  });
};

export const getProductionIssue = (issueId: string | number): AxiosPromise<ProductionIssueVO> => {
  return request({
    url: '/erp/wm/productionissue/' + issueId,
    method: 'get'
  });
};

export const addProductionIssue = (data: ProductionIssueForm) => {
  return request({
    url: '/erp/wm/productionissue',
    method: 'post',
    data
  });
};

export const updateProductionIssue = (data: ProductionIssueForm) => {
  return request({
    url: '/erp/wm/productionissue',
    method: 'put',
    data
  });
};

export const delProductionIssue = (issueIds: string | number | (string | number)[]) => {
  return request({
    url: '/erp/wm/productionissue/' + issueIds,
    method: 'delete'
  });
};

export const executeProductionIssue = (issueId: string | number) => {
  return request({
    url: '/erp/wm/productionissue/execute/' + issueId,
    method: 'put'
  });
};
