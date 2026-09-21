import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { DeviceLifecycleForm, DeviceOpsVO, DeviceQuery, DeviceVO, DeviceForm } from './types';

export const listDevice = (query: DeviceQuery): AxiosPromise<DeviceVO[]> => {
  return request({
    url: '/iot/device/list',
    method: 'get',
    params: query
  });
};

export const getDevice = (deviceId: string | number): AxiosPromise<DeviceVO> => {
  return request({
    url: '/iot/device/' + deviceId,
    method: 'get'
  });
};

export const addDevice = (data: DeviceForm) => {
  return request({
    url: '/iot/device',
    method: 'post',
    data
  });
};

export const updateDevice = (data: DeviceForm) => {
  return request({
    url: '/iot/device',
    method: 'put',
    data
  });
};

export const delDevice = (deviceId: string | number | (string | number)[]) => {
  return request({
    url: '/iot/device/' + deviceId,
    method: 'delete'
  });
};

/**
 * 查询设备运维详情：在线状态 / 最后在线时间 / 最后上报时间 / 固件版本 / 分组与标签。
 * 权限码 iot:device:query（后端 `GET /iot/device/{deviceId}/ops`）。
 */
export const deviceOps = (deviceId: string | number): AxiosPromise<DeviceOpsVO> => {
  return request({
    url: `/iot/device/${deviceId}/ops`,
    method: 'get'
  });
};

/**
 * 变更设备生命周期状态（CAS 条件更新）。
 * 权限码 iot:device:changeStatus（后端 `PUT /iot/device/{deviceId}/lifecycle`）。
 * 注销为终态；禁用/注销后 MQTT 认证立即被拒。
 */
export const changeDeviceLifecycle = (deviceId: string | number, data: DeviceLifecycleForm) => {
  return request({
    url: `/iot/device/${deviceId}/lifecycle`,
    method: 'put',
    data
  });
};

/** 供 MES 设备关联选择用的 IoT 设备列表（复用 list 接口，精简字段） */
export const listIotDeviceForBind = (query?: any): AxiosPromise<DeviceVO[]> => {
  return request({
    url: '/iot/device/list',
    method: 'get',
    params: query
  });
};
