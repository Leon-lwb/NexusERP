import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import type { QcInspectDetailVO } from './iqc';

/** 出货检验视图对象 */
export interface QcOqcVO {
  oqcId: number;
  oqcNo: string;
  soId?: number;
  materialId: number;
  materialCode?: string;
  materialName?: string;
  quantity?: number;
  sampleQty?: number;
  inspectResult: string;
  defectInfo?: string;
  inspectorId?: number;
  inspectorName?: string;
  inspectTime?: string;
  status: string;
  remark?: string;
}

/** 出货检验查询条件 */
export interface QcOqcQuery extends PageQuery {
  oqcNo?: string;
  status?: string;
  inspectResult?: string;
  soId?: number;
  materialId?: number;
}

/** 出货检验新增表单 */
export interface QcOqcForm {
  soId?: number;
  materialId: number;
  materialCode?: string;
  materialName?: string;
  quantity?: number;
  sampleQty?: number;
  remark?: string;
}

/** 分页查询OQC列表 */
export const listOqc = (query?: QcOqcQuery): AxiosPromise<QcOqcVO[]> => request({ url: '/mes/qc/oqc/list', method: 'get', params: query });

/** 获取OQC详情 */
export const getOqc = (oqcId: string | number): AxiosPromise<QcOqcVO> => request({ url: `/mes/qc/oqc/${oqcId}`, method: 'get' });

/** 新增OQC检验单 */
export const addOqc = (data: QcOqcForm): AxiosPromise<QcOqcVO> => request({ url: '/mes/qc/oqc', method: 'post', data });

/** 执行OQC检验 */
export const inspectOqc = (oqcId: string | number, inspectResult: string, defectInfo?: string, sampleQty?: number, remark?: string) =>
  request({
    url: `/mes/qc/oqc/inspect/${oqcId}`,
    method: 'put',
    params: { inspectResult, defectInfo, sampleQty, remark }
  });

/** 查询OQC检验单明细(检验项目) */
export const getOqcDetails = (oqcId: string | number): AxiosPromise<QcInspectDetailVO[]> =>
  request({ url: `/mes/qc/oqc/${oqcId}/details`, method: 'get' });
