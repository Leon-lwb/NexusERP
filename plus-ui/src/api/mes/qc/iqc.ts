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

/** 检验单明细视图对象(按检验模板快照生成) */
export interface QcInspectDetailVO {
  detailId: number;
  checkId: number;
  checkType: string;
  templateId?: number;
  itemId?: number;
  itemCode: string;
  itemName: string;
  dataType: string;
  standardType?: string;
  standardDesc?: string;
  unit?: string;
  criticality?: string;
  sampleQty?: number;
  measuredValue?: string;
  judgeResult?: string;
  seqNum?: number;
  remark?: string;
}

/** 查询IQC检验单明细(检验项目) */
export const getIqcDetails = (iqcId: string | number): AxiosPromise<QcInspectDetailVO[]> =>
  request({ url: `/mes/qc/iqc/${iqcId}/details`, method: 'get' });

/** 按明细自动判定检验(实测值 + 系统比对标准) */
export const inspectIqcWithDetails = (
  iqcId: string | number,
  data: { details: Array<{ detailId: number; measuredValue: string; sampleQty?: number; remark?: string }>; defectInfo?: string; remark?: string }
) => request({ url: `/mes/qc/iqc/inspect-details/${iqcId}`, method: 'put', data });
