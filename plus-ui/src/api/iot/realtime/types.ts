/** SSE realtime frame pushed by /iot/device/{id}/realtime */
export interface RealtimeFrame {
  deviceId: number;
  timestamp: number;
  connectionStatus: string;
  properties: Record<string, string>;
}

/** iot_device_data row from /iot/device/{id}/data */
export interface DeviceDataVO {
  dataId: number;
  deviceId: number;
  deviceCode: string;
  propertyCode: string;
  propertyName: string;
  dataType: string;
  numericValue: number;
  textValue: string;
  boolValue: boolean;
  collectTime: string;
  receiveTime: string;
  quality: string;
  source: string;
  createTime: string;
}
