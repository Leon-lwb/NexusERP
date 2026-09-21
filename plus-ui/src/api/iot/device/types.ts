export interface DeviceQuery extends PageQuery {
  deviceCode?: string;
  deviceName?: string;
  protocolType?: string;
  connectionStatus?: string;
  status?: string;
  /** 分组筛选：目标分组ID（后端 IotDeviceBo.groupId，仅查询条件） */
  groupId?: string | number | null;
  /** 分组筛选：是否包含子分组（仅 groupId 非空时生效） */
  includeSubGroup?: boolean | null;
  /** 标签筛选：标签ID集合（多标签取交集，即"同时具备这些标签"） */
  tagIds?: Array<string | number> | null;
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

/**
 * 设备生命周期状态变更入参（后端 IotDeviceStatusBo）。
 * <p>
 * 白名单 ACTIVE/INACTIVE/DECOMMISSIONED；注销为终态不可复活。
 * 注意：设备新增/修改接口已忽略前端传入的 status/connectionStatus，
 * 状态只能通过 `PUT /iot/device/{deviceId}/lifecycle` 变更。
 */
export interface DeviceLifecycleForm {
  /** ACTIVE(激活) / INACTIVE(禁用) / DECOMMISSIONED(注销) */
  status: string;
  /** 变更原因/备注 */
  remark?: string;
}

/**
 * 设备运维详情视图对象（后端 IotDeviceOpsVo，`GET /iot/device/{deviceId}/ops`）。
 * 雪花 ID 一律按 string 处理。
 */
export interface DeviceOpsVO {
  deviceId: string;
  deviceCode?: string;
  deviceName?: string;
  productId?: string;
  productName?: string;
  protocolType?: string;
  /** 生命周期状态: ACTIVE/INACTIVE/DECOMMISSIONED */
  status?: string;
  /** 连接状态: ONLINE/OFFLINE */
  connectionStatus?: string;
  lastOnlineTime?: string;
  /** 最后数据上报时间（Redis iot:lastreport） */
  lastReportTime?: string;
  firmwareVersion?: string;
  ipAddress?: string;
  location?: string;
  mesEquipmentId?: string;
  /** 所属分组ID集合（多对多） */
  groupIds?: string[];
  /** 已绑定标签ID集合（多对多） */
  tagIds?: string[];
}
