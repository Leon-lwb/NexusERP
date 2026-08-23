import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { AttendanceQuery, AttendanceVO, AttendanceForm } from './types';

export type { AttendanceQuery, AttendanceVO, AttendanceForm };

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

// ===== 人脸验证 FAIL 复查 =====

/** 标记误判(人工改 PASS) */
export const markFacePass = (attendanceId: string | number, remark?: string): AxiosPromise<void> => {
  return request({
    url: `/erp/sch/attendance/${attendanceId}/face/mark-pass`,
    method: 'put',
    params: remark ? { remark } : {}
  });
};

/** 标记代打卡(确认 FAIL) */
export const markFaceProxy = (attendanceId: string | number, remark?: string): AxiosPromise<void> => {
  return request({
    url: `/erp/sch/attendance/${attendanceId}/face/mark-proxy`,
    method: 'put',
    params: remark ? { remark } : {}
  });
};
