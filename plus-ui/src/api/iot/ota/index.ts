import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { OtaBatchForm, OtaBatchResultVO, OtaReportForm, OtaTaskForm, OtaTaskQuery, OtaTaskVO, OtaVersionCheckVO } from './types';

/**
 * 分页查询升级任务（后端 `GET /iot/ota/list`，deviceId/firmwareId/status/batchNo + PageQuery）。
 * 权限码 `iot:ota:list`。
 */
export const listOtaTask = (query: OtaTaskQuery): AxiosPromise<OtaTaskVO[]> => {
  return request({ url: '/iot/ota/list', method: 'get', params: query });
};

/** 升级任务详情（权限码 `iot:ota:query`） */
export const getOtaTask = (taskId: string | number): AxiosPromise<OtaTaskVO> => {
  return request({ url: '/iot/ota/' + taskId, method: 'get' });
};

/** 单设备下发升级任务（权限码 `iot:ota:create`） */
export const createOtaTask = (data: OtaTaskForm): AxiosPromise<OtaTaskVO> => {
  return request({ url: '/iot/ota/create', method: 'post', data });
};

/** 按分组批量下发（部分成功语义：被禁用/重复设备跳过并回传原因；权限码 `iot:ota:create`） */
export const createOtaTaskBatch = (data: OtaBatchForm): AxiosPromise<OtaBatchResultVO> => {
  return request({ url: '/iot/ota/batch', method: 'post', data });
};

/** 人工重试（仅 FAILED/TIMEOUT，后端 CAS 校验；权限码 `iot:ota:retry`） */
export const retryOtaTask = (taskId: string | number): AxiosPromise<OtaTaskVO> => {
  return request({ url: `/iot/ota/${taskId}/retry`, method: 'put' });
};

/** 人工取消（仅 PENDING/SENT/UPGRADING；权限码 `iot:ota:cancel`） */
export const cancelOtaTask = (taskId: string | number, reason?: string): AxiosPromise<OtaTaskVO> => {
  return request({ url: `/iot/ota/${taskId}/cancel`, method: 'put', params: { reason } });
};

/** 回执上报（网关/运维代报；权限码 `iot:ota:report`） */
export const reportOtaTask = (data: OtaReportForm): AxiosPromise<OtaTaskVO> => {
  return request({ url: '/iot/ota/report', method: 'post', data });
};

/**
 * 版本一致性核对：返回"设备当前版本 ≠ 目标版本"的在用设备清单。
 * 权限码 `iot:ota:versionCheck`。
 *
 * @param firmwareId 固件ID
 * @param onlyBehind 仅返回落后设备（默认 false）
 */
export const checkOtaVersion = (firmwareId: string | number, onlyBehind = false): AxiosPromise<OtaVersionCheckVO[]> => {
  return request({ url: '/iot/ota/versionCheck', method: 'get', params: { firmwareId, onlyBehind } });
};
