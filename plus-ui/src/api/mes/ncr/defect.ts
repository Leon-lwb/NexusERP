import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 缺陷明细视图对象 */
export interface NcrDefectVO {
  defectId: number;
  ncrId: number;
  defectCodeId?: number;
  defectCode?: string;
  defectName?: string;
  defectQty?: number;
  defectCause?: string;
  rootCauseDesc?: string;
  remark?: string;
}

/** 缺陷明细表单 */
export interface NcrDefectForm {
  defectId?: number;
  ncrId: number;
  defectCodeId?: number;
  defectQty?: number;
  defectCause?: string;
  rootCauseDesc?: string;
  remark?: string;
}

/** 查询缺陷明细列表(按NCR ID) */
export const listDefect = (ncrId: number | string): AxiosPromise<NcrDefectVO[]> =>
  request({ url: '/mes/ncr/defect/list', method: 'get', params: { ncrId } });

/** 新增缺陷明细 */
export const addDefect = (data: NcrDefectForm) => request({ url: '/mes/ncr/defect', method: 'post', data });
