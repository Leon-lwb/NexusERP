import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { DeviceEventProcessForm, DeviceEventQuery, DeviceEventVO } from './types';

/** 分页查询设备事件（权限码 iot:event:list） */
export const listDeviceEvent = (query: DeviceEventQuery): AxiosPromise<DeviceEventVO[]> => {
  return request({ url: '/iot/event/list', method: 'get', params: query });
};

/** 事件详情（含载荷 JSON；权限码 iot:event:query） */
export const getDeviceEvent = (eventId: string | number): AxiosPromise<DeviceEventVO> => {
  return request({ url: '/iot/event/' + eventId, method: 'get' });
};

/** 标记事件处理状态：已处理/已忽略/处理失败（权限码 iot:event:process） */
export const processDeviceEvent = (data: DeviceEventProcessForm) => {
  return request({ url: '/iot/event/process', method: 'put', data });
};

/** 导出设备事件（权限码 iot:event:export） */
export const exportDeviceEvent = (query: DeviceEventQuery): AxiosPromise<Blob> => {
  return request({ url: '/iot/event/export', method: 'post', params: query, responseType: 'blob' });
};
