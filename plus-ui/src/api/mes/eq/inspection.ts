import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 点检结果项 */
export interface InspectionItemResult {
  item: string;
  standard: string;
  actual: string;
  pass: boolean;
}

/** 点检结果提交 BO */
export interface InspectionResultBo {
  results: InspectionItemResult[];
  overallPass: boolean;
  remark?: string;
}

/** 设备点检信息(含 inspection_items) */
export interface EquipmentInspectionVO {
  equipmentId: number;
  equipmentCode: string;
  equipmentName: string;
  status: string;
  inspectionItems?: string; // JSON 字符串
  lastInspection?: string; // JSON 字符串
}

/** 查询待点检设备列表 */
export const listEquipmentForInspection = (params?: any): AxiosPromise<EquipmentInspectionVO[]> => {
  return request({ url: '/mes/eq/equipment/list', method: 'get', params });
};

/** 提交点检结果 */
export const submitInspection = (equipmentId: number, data: InspectionResultBo): AxiosPromise<void> => {
  return request({ url: `/mes/eq/equipment/${equipmentId}/inspect`, method: 'post', data });
};
