/**
 * IoT 设备事件类型定义（后端 `/iot/event`，见 doc/audit/2026-09-20-feat-iot-device-ops.md §7）。
 * <p>
 * 所有 ID（`eventId`/`deviceId`/`productId`/`alertId`）均为雪花 ID，
 * 后端已全局序列化为字符串，前端一律按 `string` 处理。
 */

/** 设备事件查询参数（后端 IotDeviceEventBo） */
export type DeviceEventQuery = PageQuery & {
  eventId?: string | null;
  deviceId?: string | null;
  /** 设备编码（模糊） */
  deviceCode?: string | null;
  productId?: string | null;
  /** 事件编码（模糊） */
  eventCode?: string | null;
  /** 事件级别: INFO/WARN/ERROR/CRITICAL */
  eventLevel?: string | null;
  /** 处理状态: PENDING/PROCESSED/IGNORED/FAILED */
  processStatus?: string | null;
  /** 是否仅看已联动告警的事件 */
  hasAlert?: boolean | null;
  /** 事件时间起（yyyy-MM-dd HH:mm:ss） */
  beginTime?: string | null;
  /** 事件时间止（yyyy-MM-dd HH:mm:ss） */
  endTime?: string | null;
};

/** 设备事件视图对象 */
export type DeviceEventVO = {
  eventId: string;
  tenantId?: string;
  deviceId?: string;
  deviceCode?: string;
  productId?: string;
  eventCode?: string;
  eventName?: string;
  /** INFO/WARN/ERROR/CRITICAL */
  eventLevel?: string;
  /** 事件发生时间（设备侧） */
  eventTime?: string;
  /** 服务端接收时间 */
  receiveTime?: string;
  /** 事件原始载荷(JSON) */
  payload?: string;
  /** 数据质量: GOOD/BAD/UNCERTAIN */
  quality?: string;
  /** PENDING/PROCESSED/IGNORED/FAILED */
  processStatus?: string;
  processRemark?: string;
  /** 联动生成的告警ID（空=未联动） */
  alertId?: string;
  /** 数据来源: MQTT/HTTP */
  source?: string;
  sourceMsgId?: string;
  createTime?: string;
};

/** 事件处理状态标记入参（后端 IotDeviceEventController.EventProcessDto） */
export type DeviceEventProcessForm = {
  eventId: string | number;
  /** PROCESSED/IGNORED/FAILED */
  processStatus: string;
  remark?: string;
};
