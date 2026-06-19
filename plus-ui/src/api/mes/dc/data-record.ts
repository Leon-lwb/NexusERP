import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 采集数据记录视图对象 */
export interface DcDataRecordVO {
  recordId: number;
  pointId: number;
  pointCode?: string;
  mesWoId?: number;
  cardId?: number;
  stepRecordId?: number;
  numericValue?: number;
  textValue?: string;
  collectTime: string;
  collectBy?: number;
  source: string;
  remark?: string;
}

/** 数据记录查询条件 */
export interface DcDataRecordQuery extends PageQuery {
  pointId?: number;
  mesWoId?: number;
  cardId?: number;
  source?: string;
}

/** 数据记录录入表单 */
export interface DcDataRecordForm {
  pointId: number;
  mesWoId?: number;
  cardId?: number;
  stepRecordId?: number;
  numericValue?: number;
  textValue?: string;
  collectTime?: string;
  source?: string;
  remark?: string;
}

/** 分页查询数据记录 */
export const listDataRecord = (query?: DcDataRecordQuery): AxiosPromise<DcDataRecordVO[]> =>
  request({ url: '/mes/dc/data-record/list', method: 'get', params: query });

/** 获取数据记录详情 */
export const getDataRecord = (recordId: string | number): AxiosPromise<DcDataRecordVO> =>
  request({ url: `/mes/dc/data-record/${recordId}`, method: 'get' });

/** 录入采集数据 */
export const addDataRecord = (data: DcDataRecordForm): AxiosPromise<DcDataRecordVO> => request({ url: '/mes/dc/data-record', method: 'post', data });
