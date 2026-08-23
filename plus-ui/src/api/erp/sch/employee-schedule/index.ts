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

/** 按计划生成员工排班明细(按班组排班模板展开; planId 空则处理全部已发布计划) */
export const generateEmployeeSchedule = (planId?: number | string) => {
  return request({
    url: '/erp/sch/employeeSchedule/generate',
    method: 'post',
    params: planId ? { planId } : {}
  });
};
