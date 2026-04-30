import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { ReportQuery, ReportVO, ReportForm } from './types';

export const listReport = (query: ReportQuery): AxiosPromise<ReportVO[]> => {
  return request({
    url: '/erp/prd/report/list',
    method: 'get',
    params: query
  });
};

export const getReport = (reportId: string | number): AxiosPromise<ReportVO> => {
  return request({
    url: '/erp/prd/report/' + reportId,
    method: 'get'
  });
};

export const addReport = (data: ReportForm) => {
  return request({
    url: '/erp/prd/report',
    method: 'post',
    data
  });
};

export const updateReport = (data: ReportForm) => {
  return request({
    url: '/erp/prd/report',
    method: 'put',
    data
  });
};

export const delReport = (reportId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/prd/report/' + reportId,
    method: 'delete'
  });
};
