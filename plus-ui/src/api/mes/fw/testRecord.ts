import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 测试记录视图对象(SN级全检, 一单多行) */
export interface FwTestRecordVO {
  testRecordId: number;
  testNo?: string;
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
  testItemId?: number;
  itemCode?: string;
  itemName?: string;
  dataType?: string;
  unit?: string;
  numericValue?: number;
  textValue?: string;
  upperLimit?: number;
  lowerLimit?: number;
  targetValue?: number;
  passEnum?: string;
  result?: string;
  testerId?: number;
  testerName?: string;
  equipmentId?: number;
  equipmentCode?: string;
  testTime?: string;
  testMode?: string;
  remark?: string;
  createTime?: string;
}

/** 测试记录查询条件 */
export interface FwTestRecordQuery extends PageQuery {
  testNo?: string;
  snCode?: string;
  woNo?: string;
  firmwareId?: number;
  testItemId?: number;
  result?: string;
  testMode?: string;
  testerId?: number;
  beginTestTime?: string;
  endTestTime?: string;
}

/** 测试开单/填报表单 */
export interface FwTestRecordForm {
  testRecordId?: number;
  testNo?: string;
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
  testItemId?: number;
  itemCode?: string;
  itemName?: string;
  dataType?: string;
  unit?: string;
  numericValue?: number;
  textValue?: string;
  upperLimit?: number;
  lowerLimit?: number;
  targetValue?: number;
  passEnum?: string;
  result?: string;
  testerId?: number;
  testerName?: string;
  equipmentId?: number;
  equipmentCode?: string;
  testTime?: string;
  testMode?: string;
  remark?: string;
}

/** 分页查询测试记录 */
export const listTestRecord = (query?: FwTestRecordQuery): AxiosPromise<FwTestRecordVO[]> =>
  request({ url: '/mes/fw/test-record/list', method: 'get', params: query });

/** 获取测试记录详情 */
export const getTestRecord = (testRecordId: number | string): AxiosPromise<FwTestRecordVO> =>
  request({ url: `/mes/fw/test-record/${testRecordId}`, method: 'get' });

/** 按测试单号查询全部行 */
export const getTestRecordByTestNo = (testNo: string): AxiosPromise<FwTestRecordVO[]> =>
  request({ url: `/mes/fw/test-record/test-no/${testNo}`, method: 'get' });

/** 开单: SN 匹配测试项生成待测行 */
export const beginTest = (data: FwTestRecordForm): AxiosPromise<FwTestRecordVO[]> =>
  request({ url: '/mes/fw/test-record/begin', method: 'post', data });

/** 填报: 批量提交实测值并自动判定 */
export const reportTestRows = (rows: FwTestRecordForm[]): AxiosPromise<FwTestRecordVO[]> =>
  request({ url: '/mes/fw/test-record/report', method: 'post', data: rows });

/** 批量删除测试记录行(仅未判定草稿行可删) */
export const delTestRecord = (ids: (number | string)[]) =>
  request({ url: `/mes/fw/test-record/${ids.join(',')}`, method: 'delete' });

/** 测试统计概览 */
export const getTestSummary = (query?: FwTestRecordQuery): AxiosPromise<Record<string, number>> =>
  request({ url: '/mes/fw/test-record/summary', method: 'get', params: query });