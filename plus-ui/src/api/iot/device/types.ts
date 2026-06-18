export interface DeviceQuery extends PageQuery {
  deviceCode?: string;
  deviceName?: string;
  protocolType?: string;
  connectionStatus?: string;
  status?: string;
}

export interface DeviceVO {
  deviceId: number;
  deviceCode: string;
  deviceName: string;
  productId: number;
  productName: string;
  protocolType: string;
  connectionStatus: string;
  lastOnlineTime: string;
  deviceSecret: string;
  ipAddress: string;
  firmwareVersion: string;
  location: string;
  mesEquipmentId: number;
  status: string;
  remark: string;
  createTime: string;
}

export interface DeviceForm {
  deviceId?: number;
  deviceCode: string;
  deviceName: string;
  productId: number;
  protocolType?: string;
  ipAddress?: string;
  firmwareVersion?: string;
  location?: string;
  mesEquipmentId?: number;
  status?: string;
  remark?: string;
}
