import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { DeviceQuery, DeviceVO, DeviceForm } from './types';

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

/** 供 MES 设备关联选择用的 IoT 设备列表（复用 list 接口，精简字段） */
export const listIotDeviceForBind = (query?: any): AxiosPromise<DeviceVO[]> => {
  return request({
    url: '/iot/device/list',
    method: 'get',
    params: query
  });
};
