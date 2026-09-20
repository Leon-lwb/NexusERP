/**
 * IoT 下行指令类型定义（后端 `/iot/command`，见 doc/audit/2026-09-20-feat-iot-device-ops.md §7）。
 * <p>
 * 所有 ID（`commandId`/`deviceId`）均为雪花 ID，后端已全局序列化为字符串，前端按 `string` 处理。
 */

/** 指令留痕查询参数（后端 IotDeviceCommandController#list） */
export type DeviceCommandQuery = PageQuery & {
  deviceId?: string | null;
  /** 状态: PENDING/SENT/SUCCESS/FAILED */
  status?: string | null;
};

/** 指令留痕视图对象 */
export type DeviceCommandVO = {
  commandId: string;
  deviceId?: string;
  deviceCode?: string;
  /** PROPERTY_SET/SERVICE_INVOKE */
  commandType?: string;
  topic?: string;
  /** 下发载荷(JSON) */
  payload?: string;
  /** PENDING/SENT/SUCCESS/FAILED */
  status?: string;
  retryCount?: number;
  sentTime?: string;
  ackTime?: string;
  /** 设备回执载荷(JSON) */
  ackPayload?: string;
  ackMsgId?: string;
  resultMessage?: string;
  operatorId?: string;
  operatorName?: string;
  createTime?: string;
  updateTime?: string;
};

/** 指令下发表单（后端 IotDeviceCommandBo） */
export type DeviceCommandForm = {
  deviceId: string | number | null;
  /** PROPERTY_SET/SERVICE_INVOKE */
  commandType?: string;
  /** 服务名（SERVICE_INVOKE 必填） */
  serviceName?: string;
  /** 属性设置时为 属性编码 → 期望值；服务调用时为入参 */
  params?: Record<string, any>;
  remark?: string;
};
