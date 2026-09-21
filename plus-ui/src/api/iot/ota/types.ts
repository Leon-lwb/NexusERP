/**
 * IoT OTA 固件升级任务类型定义（后端 `/iot/ota`，见 doc/audit/2026-09-20-feat-iot-ota.md §6.1/§6.3）。
 * <p>
 * 所有 ID（`taskId`/`firmwareId`/`deviceId`/`groupId`/`commandId`）均为雪花 ID，
 * 后端已全局序列化为字符串，前端一律按 `string` 处理。
 * <p>
 * 状态机：PENDING → SENT → UPGRADING → SUCCESS/FAILED；SENT/UPGRADING 超时 → TIMEOUT；
 * 进行中可人工 → CANCELLED。终态不可再流转（回执 CAS 拒绝）。
 */

/** 升级任务查询参数（后端 IotOtaController#list：deviceId/firmwareId/status/batchNo + PageQuery） */
export type OtaTaskQuery = PageQuery & {
  deviceId?: string | null;
  firmwareId?: string | null;
  /** PENDING/SENT/UPGRADING/SUCCESS/FAILED/TIMEOUT/CANCELLED */
  status?: string | null;
  /** 批量下发批次号 */
  batchNo?: string | null;
};

/** 升级任务视图对象（后端 IotOtaTask） */
export type OtaTaskVO = {
  taskId: string;
  tenantId?: string;
  firmwareId?: string;
  /** 目标版本（冗余，固件删除后仍可追溯） */
  targetVersion?: string;
  /** 下发时设备当前版本 */
  currentVersion?: string;
  deviceId?: string;
  deviceCode?: string;
  /** 来源分组ID（按分组批量下发时记录） */
  groupId?: string;
  batchNo?: string;
  /** 关联下行指令ID（iot_device_command） */
  commandId?: string;
  status?: string;
  /** 0-100 */
  progress?: number;
  retryCount?: number;
  sentTime?: string;
  startTime?: string;
  finishTime?: string;
  failReason?: string;
  reportMsgId?: string;
  operatorId?: string;
  operatorName?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
};

/** 单设备下发入参（后端 IotOtaTaskBo：deviceId/firmwareId 必填） */
export type OtaTaskForm = {
  deviceId: string | number;
  firmwareId: string | number;
  remark?: string | null;
};

/** 按分组批量下发入参（后端 IotOtaBatchTaskBo：firmwareId/groupId 必填） */
export type OtaBatchForm = {
  firmwareId: string | number;
  groupId: string | number;
  /** 是否包含子分组（默认仅本分组） */
  includeSubGroup?: boolean;
  remark?: string | null;
};

/** 批量下发结果（后端 IotOtaBatchResultVo，部分成功语义，不整体回滚） */
export type OtaBatchResultVO = {
  batchNo?: string;
  total?: number;
  created?: number;
  skipped?: number;
  tasks?: OtaTaskVO[];
  /** 逐条跳过原因，格式：设备[deviceId]未创建：原因 */
  messages?: string[];
};

/**
 * 回执上报入参（后端 IotOtaReportBo）。
 * <p>
 * 设备直连 MQTT 时走 `event/post` 的 `OTA_PROGRESS`/`OTA_RESULT` 事件旁路，无需本接口；
 * 本接口供设备网关/运维代报（权限码 `iot:ota:report`）。
 */
export type OtaReportForm = {
  /** 与 deviceCode 至少提供一个 */
  deviceId?: string | number | null;
  deviceCode?: string | null;
  /** 可空；空则后端匹配该设备最近一条进行中任务 */
  taskId?: string | number | null;
  /** UPGRADING（进度）/ SUCCESS / FAILED */
  status: string;
  /** 0-100（SUCCESS 时后端强制 100，且进度单调不回退） */
  progress?: number | null;
  /** 设备上报的当前版本（仅留档） */
  version?: string | null;
  failReason?: string | null;
  /** 幂等/取证键 */
  msgId?: string | null;
};

/** 版本一致性核对结果（后端 IotOtaVersionCheckVo） */
export type OtaVersionCheckVO = {
  deviceId: string;
  deviceCode?: string;
  deviceName?: string;
  /** 设备当前版本（可能为空=未知） */
  currentVersion?: string;
  targetVersion?: string;
  /** 只返回不一致设备，故恒为 false（保留字段便于统一渲染） */
  match?: boolean;
  /** 是否落后于目标版本（分段数值比较；空版本视为落后） */
  behind?: boolean;
};
