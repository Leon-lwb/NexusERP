import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { EmployeeScheduleQuery, EmployeeScheduleVO, EmployeeScheduleForm } from './types';

export const listEmployeeSchedule = (query: EmployeeScheduleQuery): AxiosPromise<EmployeeScheduleVO[]> => {
  return request({
    url: '/erp/sch/employeeSchedule/list',
    method: 'get',
    params: query
  });
};

export const getEmployeeSchedule = (scheduleId: string | number): AxiosPromise<EmployeeScheduleVO> => {
  return request({
    url: '/erp/sch/employeeSchedule/' + scheduleId,
    method: 'get'
  });
};

export const addEmployeeSchedule = (data: EmployeeScheduleForm) => {
  return request({
    url: '/erp/sch/employeeSchedule',
    method: 'post',
    data
  });
};

export const updateEmployeeSchedule = (data: EmployeeScheduleForm) => {
  return request({
    url: '/erp/sch/employeeSchedule',
    method: 'put',
    data
  });
};

export const delEmployeeSchedule = (scheduleId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/sch/employeeSchedule/' + scheduleId,
    method: 'delete'
  });
};
