import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 固件档案视图对象 */
export interface FwFirmwareVO {
  firmwareId: number;
  firmwareCode: string;
  firmwareName: string;
  materialId: number;
  materialCode?: string;
  materialName?: string;
  version: string;
  buildNo?: string;
  ossId?: number;
  fileUrl?: string;
  fileMd5?: string;
  fileSize?: number;
  releaseNote?: string;
  status: string;
  isDefault?: string;
  selectFlowId?: number;
  publishedBy?: number;
  publishedByName?: string;
  publishTime?: string;
  createBy?: number;
  createByName?: string;
  remark?: string;
  createTime?: string;
}

/** 固件档案查询条件 */
export interface FwFirmwareQuery extends PageQuery {
  firmwareCode?: string;
  firmwareName?: string;
  materialId?: number;
  status?: string;
}

/** 固件档案表单(新增/编辑) */
export interface FwFirmwareForm {
  firmwareId?: number;
  firmwareCode?: string;
  firmwareName: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  version: string;
  buildNo?: string;
  ossId?: number;
  fileMd5?: string;
  fileSize?: number;
  releaseNote?: string;
  remark?: string;
}

/** 固件文件上传返回 */
export interface FwUploadResult {
  ossId: number;
  fileName: string;
  fileSize: number;
  md5: string;
}

/** 分页查询固件档案列表 */
export const listFirmware = (query?: FwFirmwareQuery): AxiosPromise<FwFirmwareVO[]> =>
  request({ url: '/mes/fw/firmware/list', method: 'get', params: query });

/** 获取固件详情 */
export const getFirmware = (firmwareId: number | string): AxiosPromise<FwFirmwareVO> =>
  request({ url: `/mes/fw/firmware/${firmwareId}`, method: 'get' });

/** 新增固件(编码自动生成 + 状态 DRAFT) */
export const addFirmware = (data: FwFirmwareForm) => request({ url: '/mes/fw/firmware', method: 'post', data });

/** 修改固件(仅 DRAFT/ARCHIVED 可编辑) */
export const updateFirmware = (data: FwFirmwareForm) => request({ url: '/mes/fw/firmware', method: 'put', data });

/** 批量删除固件(仅 DRAFT 可删) */
export const delFirmware = (firmwareIds: number | number[]) =>
  request({ url: `/mes/fw/firmware/${firmwareIds}`, method: 'delete' });

/** 上传固件文件(MinIO, 返回 ossId/fileSize/md5 回填) */
export const uploadFirmware = (file: File): AxiosPromise<FwUploadResult> => {
  const formData = new FormData();
  formData.append('file', file);
  return request({ url: '/mes/fw/firmware/upload', method: 'post', data: formData });
};

/** 提交审批: DRAFT -> IN_REVIEW (selectFlowId 非空走 Flowable) */
export const submitFirmware = (firmwareId: number | string, selectFlowId?: string) =>
  request({ url: `/mes/fw/firmware/submit/${firmwareId}`, method: 'post', params: { selectFlowId } });

/** 兜底审批通过: IN_REVIEW -> RELEASED */
export const approveFirmware = (firmwareId: number | string, remark?: string) =>
  request({ url: `/mes/fw/firmware/approve/${firmwareId}`, method: 'put', params: { remark } });

/** 兜底驳回: IN_REVIEW -> DRAFT */
export const rejectFirmware = (firmwareId: number | string, reason?: string) =>
  request({ url: `/mes/fw/firmware/reject/${firmwareId}`, method: 'put', params: { reason } });

/** 归档: RELEASED -> ARCHIVED */
export const archiveFirmware = (firmwareId: number | string, reason?: string) =>
  request({ url: `/mes/fw/firmware/archive/${firmwareId}`, method: 'put', params: { reason } });

/** 设为物料默认版本(仅 RELEASED, 物料内唯一) */
export const setDefaultFirmware = (firmwareId: number | string) =>
  request({ url: `/mes/fw/firmware/default/${firmwareId}`, method: 'put' });

/** 查询可发起审批流程列表(提交审批弹窗用) */
export const getFirmwareFlows = (): AxiosPromise<any[]> =>
  request({ url: '/mes/fw/firmware/flows', method: 'get' });

/** 查询流程实例ID(审批表单页"查看流程"跳转用) */
export const getFirmwareProcId = (firmwareId: number | string): AxiosPromise<string> =>
  request({ url: `/mes/fw/firmware/${firmwareId}/proc`, method: 'get' });