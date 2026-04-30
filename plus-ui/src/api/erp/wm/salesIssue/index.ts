import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { SalesIssueQuery, SalesIssueVO, SalesIssueForm } from './types';

export const listSalesIssue = (query: SalesIssueQuery): AxiosPromise<SalesIssueVO[]> => {
  return request({
    url: '/erp/wm/salesIssue/list',
    method: 'get',
    params: query
  });
};

export const getSalesIssue = (issueId: string | number): AxiosPromise<SalesIssueVO> => {
  return request({
    url: '/erp/wm/salesIssue/' + issueId,
    method: 'get'
  });
};

export const addSalesIssue = (data: SalesIssueForm) => {
  return request({
    url: '/erp/wm/salesIssue',
    method: 'post',
    data
  });
};

export const updateSalesIssue = (data: SalesIssueForm) => {
  return request({
    url: '/erp/wm/salesIssue',
    method: 'put',
    data
  });
};

export const delSalesIssue = (issueId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/wm/salesIssue/' + issueId,
    method: 'delete'
  });
};

export const confirmSalesIssue = (issueId: string | number) => {
  return request({
    url: '/erp/wm/salesIssue/confirm/' + issueId,
    method: 'put'
  });
};

export const completeSalesIssue = (issueId: string | number) => {
  return request({
    url: '/erp/wm/salesIssue/complete/' + issueId,
    method: 'put'
  });
};
