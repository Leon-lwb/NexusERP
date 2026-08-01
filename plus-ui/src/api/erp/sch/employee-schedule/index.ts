import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { EmployeeScheduleQuery, EmployeeScheduleVO, EmployeeScheduleForm } from './types';

// 注意: 后端 SchEmployeeScheduleController 类级映射为 camelCase /erp/sch/employeeSchedule，Spring 路径区分大小写，必须保持一致
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
