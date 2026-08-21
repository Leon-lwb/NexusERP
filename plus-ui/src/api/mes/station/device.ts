import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 工位终端设备台账视图对象(在线状态为服务端计算列) */
export interface StationDeviceVO {
  deviceId: number;
  deviceCode: string;
  stationCode?: string;
  processCode?: string;
  appVersion?: string;
  operatorCode?: string;
  operatorName?: string;
  /** 应用正常退出标记(服务端即时判离线) */
  shutdown?: boolean;
  lastHeartbeat?: string;
  online: boolean;
}

/** 台账汇总(顶卡) */
export interface StationDeviceStats {
  total: number;
  onlineCount: number;
  offlineCount: number;
}

/** 工作中心信息(bd_workstation) */
export interface WorkstationInfo {
  workstationId: number;
  workstationCode?: string;
  workstationName?: string;
  workshopCode?: string;
  workshopName?: string;
  status?: number;
}

/** 工序信息(bd_process) */
export interface ProcessInfo {
  processId: number;
  processCode: string;
  processName: string;
}

/** 生产设备(EqEquipment 摘要列) */
export interface LinkageEquipmentVO {
  equipmentId: number;
  equipmentCode: string;
  equipmentName: string;
  equipmentType?: string;
  model?: string;
  manufacturer?: string;
  location?: string;
  status?: string;
}

/** 联动视图·正向: 工位终端 → 工作中心设备清单 */
export interface StationDeviceLinkageVO {
  deviceCode: string;
  stationCode?: string;
  processCode?: string;
  processName?: string;
  workstation?: WorkstationInfo | null;
  equipments: LinkageEquipmentVO[];
  message?: string;
}

/** 联动视图·反向: 工作中心 → 工位终端清单 */
export interface WorkcenterDevicesVO {
  workstation?: WorkstationInfo | null;
  processes: ProcessInfo[];
  devices: StationDeviceVO[];
  message?: string;
}

/** 分页查询设备台账 */
export const listStationDevice = (query?: any): AxiosPromise<StationDeviceVO[]> =>
  request({ url: '/mes/station-admin/device/list', method: 'get', params: query });

/** 台账汇总 */
export const stationDeviceStats = (): AxiosPromise<StationDeviceStats> => request({ url: '/mes/station-admin/device/stats', method: 'get' });

/** 联动视图·正向: 工位终端 → 工作中心设备清单(M3) */
export const stationDeviceEquipments = (deviceId: string | number): AxiosPromise<StationDeviceLinkageVO> =>
  request({ url: `/mes/station-admin/device/${deviceId}/equipments`, method: 'get' });

/** 联动视图·反向: 工作中心 → 工位终端清单(M3, 设备台账详情页用) */
export const stationDevicesByWorkcenter = (workcenterId: string | number): AxiosPromise<WorkcenterDevicesVO> =>
  request({ url: `/mes/station-admin/device/by-workcenter/${workcenterId}`, method: 'get' });
