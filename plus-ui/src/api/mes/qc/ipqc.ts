import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 过程检验视图对象 */
export interface QcIpqcVO {
  ipqcId: number;
  ipqcNo: string;
  mesWoId: number;
  woNo?: string;
  cardId?: number;
  stepRecordId?: number;
  processId?: number;
  processCode?: string;
  processName?: string;
  materialId: number;
  materialCode?: string;
  materialName?: string;
  sampleQty?: number;
  defectQty?: number;
  defectRate?: number;
  inspectResult: string;
  defectInfo?: string;
  inspectorId?: number;
  inspectorName?: string;
  inspectTime?: string;
  status: string;
  remark?: string;
}

/** 过程检验查询条件 */
export interface QcIpqcQuery extends PageQuery {
  ipqcNo?: string;
  status?: string;
  inspectResult?: string;
  mesWoId?: number;
  woNo?: string;
  cardId?: number;
  materialId?: number;
}

/** 过程检验新增表单 */
export interface QcIpqcForm {
  mesWoId: number;
  woNo?: string;
  cardId?: number;
  stepRecordId?: number;
  processId?: number;
  processCode?: string;
  processName?: string;
  materialId: number;
  materialCode?: string;
  materialName?: string;
  sampleQty?: number;
  remark?: string;
}

/** 分页查询IPQC列表 */
export const listIpqc = (query?: QcIpqcQuery): AxiosPromise<QcIpqcVO[]> => request({ url: '/mes/qc/ipqc/list', method: 'get', params: query });

/** 获取IPQC详情 */
export const getIpqc = (ipqcId: string | number): AxiosPromise<QcIpqcVO> => request({ url: `/mes/qc/ipqc/${ipqcId}`, method: 'get' });

/** 新增IPQC检验单 */
export const addIpqc = (data: QcIpqcForm): AxiosPromise<QcIpqcVO> => request({ url: '/mes/qc/ipqc', method: 'post', data });

/** 执行IPQC检验(自动计算不良率) */
export const inspectIpqc = (
  ipqcId: string | number,
  inspectResult: string,
  defectInfo?: string,
  sampleQty?: number,
  defectQty?: number,
  remark?: string
) =>
  request({
    url: `/mes/qc/ipqc/inspect/${ipqcId}`,
    method: 'put',
    params: { inspectResult, defectInfo, sampleQty, defectQty, remark }
  });
