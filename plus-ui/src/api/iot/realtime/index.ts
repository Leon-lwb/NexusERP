import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { DeviceDataVO } from './types';

/** Get latest property values for a device (from Redis iot:latest cache) */
export const getLatest = (deviceId: string | number): AxiosPromise<Record<string, string>> => {
  return request({
    url: '/iot/device/' + deviceId + '/latest',
    method: 'get'
  });
};

/** Get device online status (from Redis iot:status cache) */
export const getStatus = (deviceId: string | number): AxiosPromise<{ connectionStatus: string }> => {
  return request({
    url: '/iot/device/' + deviceId + '/status',
    method: 'get'
  });
};

/** Get device historical data (from MySQL iot_device_data) */
export const getHistory = (deviceId: string | number, params?: { start?: string; end?: string; limit?: number }): AxiosPromise<DeviceDataVO[]> => {
  return request({
    url: '/iot/device/' + deviceId + '/data',
    method: 'get',
    params
  });
};
