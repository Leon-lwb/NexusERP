import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 保养执行记录视图对象 */
export interface EqMaintenanceRecordVO {
  recordId: number;
  planId?: number;
  equipmentId: number;
  equipmentCode?: string;
  equipmentName?: string;
  maintenanceType?: string;
  executorId?: number;
  executorName?: string;
  executeTime?: string;
  content?: string;
  result?: string;
  remark?: string;
}

/** 保养执行记录查询条件 */
export interface EqMaintenanceRecordQuery extends PageQuery {
  planId?: number;
  equipmentId?: number;
  maintenanceType?: string;
  result?: string;
}

/** 保养执行记录表单(手动保养) */
export interface EqMaintenanceRecordForm {
  recordId?: number;
  planId?: number;
  equipmentId: number;
  maintenanceType?: string;
  executeTime?: string;
  content?: string;
  result?: string;
  remark?: string;
}

/** 分页查询保养执行记录 */
export const listMaintenanceRecord = (query?: EqMaintenanceRecordQuery): AxiosPromise<EqMaintenanceRecordVO[]> =>
  request({ url: '/mes/eq/maintenance-record/list', method: 'get', params: query });

/** 获取保养执行记录详情 */
export const getMaintenanceRecord = (recordId: string | number): AxiosPromise<EqMaintenanceRecordVO> =>
  request({ url: `/mes/eq/maintenance-record/${recordId}`, method: 'get' });

/** 查询某设备的保养执行历史(设备详情页用) */
export const listMaintenanceRecordByEquipment = (equipmentId: string | number): AxiosPromise<EqMaintenanceRecordVO[]> =>
  request({ url: `/mes/eq/maintenance-record/equipment/${equipmentId}`, method: 'get' });

/** 手动新增保养执行记录 */
export const addMaintenanceRecord = (data: EqMaintenanceRecordForm): AxiosPromise<EqMaintenanceRecordVO> =>
  request({ url: '/mes/eq/maintenance-record', method: 'post', data });

/** 批量删除保养执行记录 */
export const delMaintenanceRecord = (ids: (string | number)[]) => request({ url: `/mes/eq/maintenance-record/${ids.join(',')}`, method: 'delete' });
