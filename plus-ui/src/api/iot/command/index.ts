import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { DeviceCommandForm, DeviceCommandQuery, DeviceCommandVO } from './types';

/** 分页查询指令留痕（权限码 iot:command:list） */
export const listDeviceCommand = (query: DeviceCommandQuery): AxiosPromise<DeviceCommandVO[]> => {
  return request({ url: '/iot/command/list', method: 'get', params: query });
};

/** 指令详情（含回执载荷；权限码 iot:command:query） */
export const getDeviceCommand = (commandId: string | number): AxiosPromise<DeviceCommandVO> => {
  return request({ url: '/iot/command/' + commandId, method: 'get' });
};

/** 下发属性设置指令 property/set（权限码 iot:command:send） */
export const sendPropertySet = (data: DeviceCommandForm): AxiosPromise<DeviceCommandVO> => {
  return request({ url: '/iot/command/propertySet', method: 'post', data });
};

/** 下发服务调用指令 event/set（权限码 iot:command:send） */
export const sendServiceInvoke = (data: DeviceCommandForm): AxiosPromise<DeviceCommandVO> => {
  return request({ url: '/iot/command/serviceInvoke', method: 'post', data });
};

/** 重发指令（已成功的指令后端拒绝；权限码 iot:command:resend） */
export const resendDeviceCommand = (commandId: string | number): AxiosPromise<DeviceCommandVO> => {
  return request({ url: `/iot/command/${commandId}/resend`, method: 'put' });
};
