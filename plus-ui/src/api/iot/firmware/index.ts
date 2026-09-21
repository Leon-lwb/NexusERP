import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { FirmwareForm, FirmwareQuery, FirmwareVO } from './types';

/**
 * 分页查询固件列表（后端 `GET /iot/firmware/list`，IotFirmwareBo + PageQuery）。
 * 权限码 `iot:firmware:list`。
 */
export const listFirmware = (query: FirmwareQuery): AxiosPromise<FirmwareVO[]> => {
  return request({ url: '/iot/firmware/list', method: 'get', params: query });
};

/**
 * 固件下拉列表（后端 `GET /iot/firmware/optionList`，不分页）。
 * 下发升级任务时建议只取已发布：`{ status: 'RELEASED' }`。权限码 `iot:firmware:list`。
 */
export const optionFirmware = (query?: Partial<FirmwareQuery>): AxiosPromise<FirmwareVO[]> => {
  return request({ url: '/iot/firmware/optionList', method: 'get', params: query });
};

/** 固件详情（权限码 `iot:firmware:query`） */
export const getFirmware = (firmwareId: string | number): AxiosPromise<FirmwareVO> => {
  return request({ url: '/iot/firmware/' + firmwareId, method: 'get' });
};

/** 登记固件（服务端强制 DRAFT；权限码 `iot:firmware:add`） */
export const addFirmware = (data: Partial<FirmwareForm>) => {
  return request({ url: '/iot/firmware', method: 'post', data });
};

/** 修改固件（仅草稿可改，后端对已发布固件拒绝；权限码 `iot:firmware:edit`） */
export const updateFirmware = (data: Partial<FirmwareForm>) => {
  return request({ url: '/iot/firmware', method: 'put', data });
};

/** 批量删除固件（已发布固件后端拒绝，需先停用；权限码 `iot:firmware:remove`） */
export const delFirmware = (firmwareIds: string | number | Array<string | number>) => {
  return request({ url: '/iot/firmware/' + firmwareIds, method: 'delete' });
};

/**
 * 发布/停用固件（状态机 CAS；权限码 `iot:firmware:release`）。
 * 合法流转：DRAFT→RELEASED/DISABLED、RELEASED→DISABLED、DISABLED→RELEASED；
 * 发布前置校验由后端保证（必须有固件包与校验值）。
 */
export const changeFirmwareStatus = (firmwareId: string | number, status: string, remark?: string): AxiosPromise<FirmwareVO> => {
  return request({ url: `/iot/firmware/${firmwareId}/status`, method: 'put', params: { status, remark } });
};
