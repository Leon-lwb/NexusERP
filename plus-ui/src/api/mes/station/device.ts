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

/** 分页查询设备台账 */
export const listStationDevice = (query?: any): AxiosPromise<StationDeviceVO[]> =>
  request({ url: '/mes/station-admin/device/list', method: 'get', params: query });

/** 台账汇总 */
export const stationDeviceStats = (): AxiosPromise<StationDeviceStats> =>
  request({ url: '/mes/station-admin/device/stats', method: 'get' });
