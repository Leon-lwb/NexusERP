import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { DeviceGroupAssignForm, DeviceGroupForm, DeviceGroupQuery, DeviceGroupVO } from './types';

/**
 * 查询分组列表（平铺，含各分组已挂设备数；权限码 iot:deviceGroup:list）。
 * 后端返回数组（非分页），页面用 handleTree 组树。
 */
export const listDeviceGroup = (query?: DeviceGroupQuery): AxiosPromise<DeviceGroupVO[]> => {
  return request({ url: '/iot/deviceGroup/list', method: 'get', params: query });
};

/** 分组详情（权限码 iot:deviceGroup:query） */
export const getDeviceGroup = (groupId: string | number): AxiosPromise<DeviceGroupVO> => {
  return request({ url: '/iot/deviceGroup/' + groupId, method: 'get' });
};

/** 新增分组（权限码 iot:deviceGroup:add） */
export const addDeviceGroup = (data: Partial<DeviceGroupForm>) => {
  return request({ url: '/iot/deviceGroup', method: 'post', data });
};

/** 修改分组（父级变更时后端级联重写后代 ancestors；权限码 iot:deviceGroup:edit） */
export const updateDeviceGroup = (data: Partial<DeviceGroupForm>) => {
  return request({ url: '/iot/deviceGroup', method: 'put', data });
};

/** 批量删除分组（存在子分组或已挂设备时后端拒绝；权限码 iot:deviceGroup:remove） */
export const delDeviceGroup = (groupIds: string | number | Array<string | number>) => {
  return request({ url: '/iot/deviceGroup/' + groupIds, method: 'delete' });
};

/** 批量把设备挂入/移出分组（权限码 iot:deviceGroup:assign） */
export const assignDeviceGroup = (data: DeviceGroupAssignForm) => {
  return request({ url: '/iot/deviceGroup/assign', method: 'put', data });
};

/** 查询分组（含子树）下的设备ID集合（权限码 iot:deviceGroup:query） */
export const groupDeviceIds = (groupId: string | number, includeSubGroup = false): AxiosPromise<string[]> => {
  return request({ url: `/iot/deviceGroup/${groupId}/deviceIds`, method: 'get', params: { includeSubGroup } });
};
