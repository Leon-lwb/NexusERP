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
  /** IoT 在线状态（前端批量查询后填充，'ONLINE'|'OFFLINE'|'UNBOUND'|'UNKNOWN'） */
  iotStatus?: string;
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

/** 查询 MES 设备关联的 IoT 在线状态 */
export const getIotStatus = (equipmentId: number | string): AxiosPromise<string> =>
  request({ url: `/mes/eq/equipment/${equipmentId}/iotStatus`, method: 'get' });

/** 查询 MES 设备关联的 IoT 最新数据 */
export const getIotLatest = (equipmentId: number | string): AxiosPromise<Record<string, string>> =>
  request({ url: `/mes/eq/equipment/${equipmentId}/iotLatest`, method: 'get' });

/** 更新 MES 设备的 IoT 关联（iotDeviceId 传 null 表示解除关联） */
export const updateIotDeviceLink = (equipmentId: number | string, iotDeviceId: number | null) =>
  request({ url: `/mes/eq/equipment/${equipmentId}/iotLink`, method: 'put', data: { iotDeviceId } });

/** 供 IoT 设备关联选择用的 MES 设备列表（复用 list 接口） */
export const listMesEquipmentForBind = (query?: any): AxiosPromise<EqEquipmentVO[]> =>
  request({ url: '/mes/eq/equipment/list', method: 'get', params: query });

/** 更新 IoT 设备关联的 MES 设备（调用 IoT 侧接口，mesEquipmentId 传 null 表示解除关联） */
export const updateMesEquipmentLink = (deviceId: number | string, mesEquipmentId: number | null) =>
  request({ url: `/iot/device/${deviceId}/mesLink`, method: 'put', data: { mesEquipmentId } });
