import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 固件刷写记录视图对象 */
export interface FwFlashRecordVO {
  flashRecordId: number;
  flashNo?: string;
  snId?: number;
  snCode?: string;
  mesWoId?: number;
  woNo?: string;
  cardId?: number;
  stepRecordId?: number;
  processId?: number;
  processCode?: string;
  processName?: string;
  firmwareId?: number;
  firmwareVersion?: string;
  fileMd5?: string;
  equipmentId?: number;
  equipmentCode?: string;
  operatorId?: number;
  operatorName?: string;
  flashTime?: string;
  result: string;
  failReason?: string;
  retryCount?: number;
  flashMode: string;
  remark?: string;
  createTime?: string;
}

/** 刷写记录查询条件 */
export interface FwFlashRecordQuery extends PageQuery {
  flashNo?: string;
  snCode?: string;
  woNo?: string;
  equipmentCode?: string;
  firmwareId?: number;
  result?: string;
  flashMode?: string;
  operatorId?: number;
  beginFlashTime?: string;
  endFlashTime?: string;
}

/** 刷写记录表单(补录/作业确认) */
export interface FwFlashRecordForm {
  flashRecordId?: number;
  flashNo?: string;
  snId?: number;
  snCode?: string;
  mesWoId?: number;
  woNo?: string;
  cardId?: number;
  stepRecordId?: number;
  processId?: number;
  processCode?: string;
  processName?: string;
  firmwareId?: number;
  firmwareVersion?: string;
  fileMd5?: string;
  equipmentId?: number;
  equipmentCode?: string;
  flashTime?: string;
  result: string;
  failReason?: string;
  retryCount?: number;
  flashMode?: string;
  remark?: string;
}

/** SN 远程搜索选项 */
export interface SnOptionVO {
  snId: number;
  snCode: string;
  woNo?: string;
  cardNo?: string;
  materialCode?: string;
  materialName?: string;
  snStatus?: string;
}

/** 分页查询刷写记录 */
export const listFlash = (query?: FwFlashRecordQuery): AxiosPromise<FwFlashRecordVO[]> =>
  request({ url: '/mes/fw/flash/list', method: 'get', params: query });

/** 获取刷写记录详情 */
export const getFlash = (flashRecordId: number | string): AxiosPromise<FwFlashRecordVO> =>
  request({ url: `/mes/fw/flash/${flashRecordId}`, method: 'get' });

/** 补录刷写记录(管理员) */
export const addFlash = (data: FwFlashRecordForm) => request({ url: '/mes/fw/flash', method: 'post', data });

/** 修改刷写记录(纠错) */
export const updateFlash = (data: FwFlashRecordForm) => request({ url: '/mes/fw/flash', method: 'put', data });

/** 批量删除刷写记录(仅 MANUAL 可删) */
export const delFlash = (ids: (number | string)[]) =>
  request({ url: `/mes/fw/flash/${ids.join(',')}`, method: 'delete' });

/** 刷写作业确认(严格校验 SN 非报废/固件已发布) */
export const confirmFlash = (data: FwFlashRecordForm) => request({ url: '/mes/fw/flash/confirm', method: 'post', data });

/** SN 远程搜索 */
export const listSnOptions = (snCode?: string): AxiosPromise<SnOptionVO[]> =>
  request({ url: '/mes/fw/flash/sn-options', method: 'get', params: { snCode } });

/** 固件下拉(仅已发布 RELEASED, 作业确认可选固件) */
export const listReleasedFirmware = (): AxiosPromise<any[]> =>
  request({ url: '/mes/fw/firmware/list', method: 'get', params: { pageNum: 1, pageSize: 1000, status: 'RELEASED' } });