import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 设备台账视图对象 */
export interface EqEquipmentVO {
  equipmentId: number;
  equipmentCode: string;
  equipmentName: string;
  equipmentType?: string;
  model?: string;
  manufacturer?: string;
  serialNo?: string;
  workcenterId?: number;
  workcenterCode?: string;
  location?: string;
  purchaseDate?: string;
  warrantyDate?: string;
  status: string;
  inspectionItems?: string;
  lastInspection?: string;
  iotDeviceId?: number;
  remark?: string;
}

/** 设备台账查询条件 */
export interface EqEquipmentQuery extends PageQuery {
  equipmentCode?: string;
  equipmentName?: string;
  equipmentType?: string;
  status?: string;
  workcenterId?: number;
  iotDeviceId?: number;
}

/** 设备台账表单 */
export interface EqEquipmentForm {
  equipmentId?: number;
  equipmentCode: string;
  equipmentName: string;
  equipmentType?: string;
  model?: string;
  manufacturer?: string;
  serialNo?: string;
  workcenterId?: number;
  workcenterCode?: string;
  location?: string;
  purchaseDate?: string;
  warrantyDate?: string;
  status?: string;
  inspectionItems?: string;
  lastInspection?: string;
  iotDeviceId?: number;
  remark?: string;
}

/** 分页查询设备台账 */
export const listEquipment = (query?: EqEquipmentQuery): AxiosPromise<EqEquipmentVO[]> =>
  request({ url: '/mes/eq/equipment/list', method: 'get', params: query });

/** 获取设备详情 */
export const getEquipment = (equipmentId: string | number): AxiosPromise<EqEquipmentVO> =>
  request({ url: `/mes/eq/equipment/${equipmentId}`, method: 'get' });

/** 新增设备 */
export const addEquipment = (data: EqEquipmentForm): AxiosPromise<EqEquipmentVO> => request({ url: '/mes/eq/equipment', method: 'post', data });

/** 编辑设备 */
export const updateEquipment = (data: EqEquipmentForm): AxiosPromise<EqEquipmentVO> => request({ url: '/mes/eq/equipment', method: 'put', data });

/** 批量删除设备 */
export const delEquipment = (ids: (string | number)[]) => request({ url: `/mes/eq/equipment/${ids.join(',')}`, method: 'delete' });
