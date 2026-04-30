import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { AttendanceQuery, AttendanceVO, AttendanceForm } from './types';

export const listAttendance = (query: AttendanceQuery): AxiosPromise<AttendanceVO[]> => {
  return request({
    url: '/erp/sch/attendance/list',
    method: 'get',
    params: query
  });
};

export const getAttendance = (attendanceId: string | number): AxiosPromise<AttendanceVO> => {
  return request({
    url: '/erp/sch/attendance/' + attendanceId,
    method: 'get'
  });
};

export const addAttendance = (data: AttendanceForm) => {
  return request({
    url: '/erp/sch/attendance',
    method: 'post',
    data
  });
};

export const updateAttendance = (data: AttendanceForm) => {
  return request({
    url: '/erp/sch/attendance',
    method: 'put',
    data
  });
};

export const delAttendance = (attendanceId: string | number | (string | number)[]) => {
  return request({
    url: '/erp/sch/attendance/' + attendanceId,
    method: 'delete'
  });
};
