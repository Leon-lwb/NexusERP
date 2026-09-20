import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { DeviceTagBindForm, DeviceTagForm, DeviceTagQuery, DeviceTagVO } from './types';

/** 查询标签列表（含已绑定设备数；权限码 iot:deviceTag:list） */
export const listDeviceTag = (query?: DeviceTagQuery): AxiosPromise<DeviceTagVO[]> => {
  return request({ url: '/iot/deviceTag/list', method: 'get', params: query });
};

/** 标签下拉选项（权限码 iot:deviceTag:query） */
export const optionDeviceTag = (query?: DeviceTagQuery): AxiosPromise<DeviceTagVO[]> => {
  return request({ url: '/iot/deviceTag/optionList', method: 'get', params: query });
};

/** 标签详情（权限码 iot:deviceTag:query） */
export const getDeviceTag = (tagId: string | number): AxiosPromise<DeviceTagVO> => {
  return request({ url: '/iot/deviceTag/' + tagId, method: 'get' });
};

/** 查询某设备已绑定的标签ID集合（权限码 iot:deviceTag:query） */
export const deviceTagIds = (deviceId: string | number): AxiosPromise<string[]> => {
  return request({ url: '/iot/deviceTag/device/' + deviceId, method: 'get' });
};

/** 新增标签（权限码 iot:deviceTag:add） */
export const addDeviceTag = (data: Partial<DeviceTagForm>) => {
  return request({ url: '/iot/deviceTag', method: 'post', data });
};

/** 修改标签（权限码 iot:deviceTag:edit） */
export const updateDeviceTag = (data: Partial<DeviceTagForm>) => {
  return request({ url: '/iot/deviceTag', method: 'put', data });
};

/** 批量删除标签（级联清理设备-标签关系；权限码 iot:deviceTag:remove） */
export const delDeviceTag = (tagIds: string | number | Array<string | number>) => {
  return request({ url: '/iot/deviceTag/' + tagIds, method: 'delete' });
};

/** 批量打标签/解绑标签（权限码 iot:deviceTag:bind） */
export const bindDeviceTag = (data: DeviceTagBindForm) => {
  return request({ url: '/iot/deviceTag/bind', method: 'put', data });
};
