import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 来料检验视图对象 */
export interface QcIqcVO {
  iqcId: number;
  iqcNo: string;
  receiveId?: number;
  materialId: number;
  materialCode?: string;
  materialName?: string;
  vendorId?: number;
  vendorName?: string;
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

/** 来料检验查询条件 */
export interface QcIqcQuery extends PageQuery {
  iqcNo?: string;
  status?: string;
  inspectResult?: string;
  receiveId?: number;
  materialId?: number;
  vendorId?: number;
}

/** 来料检验新增表单 */
export interface QcIqcForm {
  receiveId?: number;
  materialId: number;
  materialCode?: string;
  materialName?: string;
  vendorId?: number;
  vendorName?: string;
  quantity?: number;
  sampleQty?: number;
  remark?: string;
}

/** 分页查询IQC列表 */
export const listIqc = (query?: QcIqcQuery): AxiosPromise<QcIqcVO[]> => request({ url: '/mes/qc/iqc/list', method: 'get', params: query });

/** 获取IQC详情 */
export const getIqc = (iqcId: string | number): AxiosPromise<QcIqcVO> => request({ url: `/mes/qc/iqc/${iqcId}`, method: 'get' });

/** 新增IQC检验单 */
export const addIqc = (data: QcIqcForm): AxiosPromise<QcIqcVO> => request({ url: '/mes/qc/iqc', method: 'post', data });

/** 执行IQC检验 */
export const inspectIqc = (iqcId: string | number, inspectResult: string, defectInfo?: string, sampleQty?: number, remark?: string) =>
  request({
    url: `/mes/qc/iqc/inspect/${iqcId}`,
    method: 'put',
    params: { inspectResult, defectInfo, sampleQty, remark }
  });
