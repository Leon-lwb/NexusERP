/**
 * IoT 数据转发规则 / 投递留痕类型定义（后端 `/iot/forwardRule`，见 doc/audit/2026-09-20-feat-iot-device-ops.md §7）。
 * <p>
 * 所有 ID（`ruleId`/`deviceId`/`productId`/`logId`）均为雪花 ID，前端按 `string` 处理。
 * 范围语义与后端一致：`productId=0` 表示不限产品、`deviceId=0` 表示不限设备。
 */

/**
 * 转发规则查询参数（后端 IotForwardRuleController#list）。
 * <p>
 * 后端该端点不分页（返回数组）；这里继承 PageQuery 仅为满足 `useCrud` 的泛型约束，
 * 多余的 pageNum/pageSize 会被 Spring 忽略。
 */
export type ForwardRuleQuery = PageQuery & {
  ruleName?: string | null;
  /** 启用状态(1启用 0停用) */
  enabled?: string | null;
};

/** 转发规则视图对象 */
export type ForwardRuleVO = {
  ruleId: string;
  tenantId?: string;
  ruleName: string;
  /** 产品范围(0=不限) */
  productId?: string;
  /** 设备范围(0=不限) */
  deviceId?: string;
  /** 属性编码白名单(逗号分隔,空=全部) */
  propertyCodes?: string;
  /** 目标类型: HTTP */
  targetType?: string;
  targetUrl?: string;
  /** POST/PUT */
  httpMethod?: string;
  /** 自定义请求头(JSON) */
  headers?: string;
  /** 单次投递超时(毫秒) */
  timeoutMs?: number;
  /** 最大重试次数 */
  maxRetry?: number;
  /** 启用状态(1启用 0停用) */
  enabled?: string;
  remark?: string;
  createTime?: string;
  updateTime?: string;
};

/** 转发规则新增/修改表单（与后端 IotForwardRuleBo 校验一致） */
export type ForwardRuleForm = {
  ruleId?: string | null;
  ruleName: string;
  productId?: string | number | null;
  deviceId?: string | number | null;
  propertyCodes?: string;
  /** 当前仅支持 HTTP */
  targetType?: string;
  targetUrl: string;
  /** POST/PUT */
  httpMethod?: string;
  headers?: string;
  /** 500~60000 */
  timeoutMs?: number;
  /** 0~10 */
  maxRetry?: number;
  enabled?: string;
  remark?: string;
};

/** 投递留痕查询参数（后端 IotForwardRuleController#logList） */
export type ForwardLogQuery = PageQuery & {
  ruleId?: string | null;
  deviceId?: string | null;
  /** 状态: PENDING/SUCCESS/FAILED */
  status?: string | null;
};

/** 投递留痕视图对象 */
export type ForwardLogVO = {
  logId: string;
  tenantId?: string;
  ruleId?: string;
  deviceId?: string;
  deviceCode?: string;
  propertyCode?: string;
  targetUrl?: string;
  /** 投递载荷(JSON) */
  payload?: string;
  /** PENDING/SUCCESS/FAILED */
  status?: string;
  /** 已重试次数 */
  retryCount?: number;
  /** HTTP响应码 */
  httpStatus?: number;
  errorMsg?: string;
  /** 下次重试时间（空=不再重试） */
  nextRetryTime?: string;
  lastAttemptTime?: string;
  createTime?: string;
  updateTime?: string;
};
