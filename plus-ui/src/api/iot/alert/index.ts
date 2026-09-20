import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { AlertRecordQuery, AlertRecordVO, AlertRecordForm, AlertNotifyLogVO } from './types';

/** 分页查询告警记录（权限码 iot:alert:list） */
export const listAlert = (query: AlertRecordQuery): AxiosPromise<AlertRecordVO[]> => {
  return request({ url: '/iot/alert/list', method: 'get', params: query });
};

/** 告警详情（权限码 iot:alert:query） */
export const getAlert = (alertId: string | number): AxiosPromise<AlertRecordVO> => {
  return request({ url: '/iot/alert/' + alertId, method: 'get' });
};

/** 确认告警 ACTIVE → ACKNOWLEDGED（权限码 iot:alert:ack） */
export const ackAlert = (data: AlertRecordForm) => {
  return request({ url: '/iot/alert/ack', method: 'post', data });
};

/** 恢复/关闭告警 ACTIVE|ACKNOWLEDGED → RESOLVED（权限码 iot:alert:recover） */
export const recoverAlert = (data: AlertRecordForm) => {
  return request({ url: '/iot/alert/recover', method: 'post', data });
};

/** 查询某条告警的通知投递留痕（权限码 iot:alert:query） */
export const notifyLog = (alertId: string | number): AxiosPromise<AlertNotifyLogVO[]> => {
  return request({ url: '/iot/alert/notifyLog/' + alertId, method: 'get' });
};

/** 导出告警记录（权限码 iot:alert:export） */
export const exportAlert = (query: AlertRecordQuery): AxiosPromise<Blob> => {
  return request({ url: '/iot/alert/export', method: 'post', params: query, responseType: 'blob' });
};
