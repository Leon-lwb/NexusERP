/**
 * IoT 告警记录 / 告警规则 类型定义（IOT-ALERT-01/02）。
 * <p>
 * 契约来源：`doc/audit/2026-09-20-feat-iot-alert.md` §7。
 * 注意：`alertId`/`ruleId`/`deviceId`/`productId`/`ackBy`/`resolveBy` 均为雪花 ID，
 * 后端已通过全局 `BigNumberSerializer` 序列化为字符串，前端一律按 string 处理，勿用 number。
 */

/** 告警记录查询参数 */
export interface AlertRecordQuery extends PageQuery {
  /** 设备ID */
  deviceId?: string;
  /** 设备编码（模糊） */
  deviceCode?: string;
  /** 产品ID */
  productId?: string;
  /** 属性编码（模糊） */
  propertyCode?: string;
  /** 状态: ACTIVE/ACKNOWLEDGED/RESOLVED */
  status?: string;
  /** 告警级别: HIGH/MEDIUM/LOW */
  alertLevel?: string;
  /** 告警类型 */
  alertType?: string;
  /** 来源: RULE/DEFAULT/ANOMALY */
  source?: string;
  /** 触发时间起（含） */
  beginTime?: string;
  /** 触发时间止（含） */
  endTime?: string;
}

/** 告警记录视图对象 */
export interface AlertRecordVO {
  alertId: string;
  tenantId?: string;
  /** 命中规则ID */
  ruleId?: string;
  source?: string;
  deviceId?: string;
  deviceCode?: string;
  productId?: string;
  propertyCode?: string;
  alertType?: string;
  alertLevel?: string;
  /** 比较符快照 */
  operator?: string;
  /** 阈值快照 */
  threshold?: number;
  triggerValue?: number;
  alertMessage?: string;
  /** 状态: ACTIVE/ACKNOWLEDGED/RESOLVED */
  status?: string;
  /** 未恢复标记(1=未恢复, NULL=已恢复) */
  activeFlag?: number;
  triggerTime?: string;
  lastTriggerTime?: string;
  ackTime?: string;
  ackBy?: string;
  ackRemark?: string;
  resolveTime?: string;
  resolveBy?: string;
  resolveValue?: number;
  resolveRemark?: string;
  createTime?: string;
}

/** 确认 / 恢复入参 */
export interface AlertRecordForm {
  alertId?: string;
  ackRemark?: string;
  resolveValue?: number;
  resolveRemark?: string;
}

/** 告警通知投递留痕视图对象 */
export interface AlertNotifyLogVO {
  logId: string;
  alertId: string;
  /** 通知渠道: SSE/MES_ANDON */
  channel?: string;
  /** 投递目标 */
  target?: string;
  /** 投递结果: SUCCESS/FAILED/SKIPPED */
  status?: string;
  message?: string;
  createTime?: string;
}

/** 告警规则查询参数 */
export interface AlertRuleQuery extends PageQuery {
  ruleName?: string;
  propertyCode?: string;
  alertType?: string;
  alertLevel?: string;
  /** 启用状态(1启用 0停用) */
  enabled?: string;
}

/** 告警规则视图对象 */
export interface AlertRuleVO {
  ruleId: string;
  tenantId?: string;
  ruleName: string;
  /** 产品范围(0=不限) */
  productId?: string;
  /** 设备范围(0=不限) */
  deviceId?: string;
  propertyCode: string;
  /** 比较符: GT/GE/LT/LE/EQ/NE */
  operator: string;
  threshold: number;
  /** 持续时长(秒) */
  durationSec?: number;
  /** 去抖窗口(秒) */
  debounceSec?: number;
  alertType?: string;
  alertLevel?: string;
  /** 通知渠道(逗号分隔): SSE/MES_ANDON */
  notifyChannels?: string;
  /** 启用状态(1启用 0停用) */
  enabled?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/** 告警规则新增/修改表单（与后端 IotAlertRuleBo 校验规则保持一致） */
export interface AlertRuleForm {
  ruleId?: string;
  ruleName: string;
  productId?: string | number | null;
  deviceId?: string | number | null;
  propertyCode: string;
  operator: string;
  threshold: number;
  /** 0~86400 */
  durationSec?: number;
  /** 0~86400 */
  debounceSec?: number;
  alertType?: string;
  alertLevel?: string;
  notifyChannels?: string;
  enabled?: string;
  remark?: string;
}
