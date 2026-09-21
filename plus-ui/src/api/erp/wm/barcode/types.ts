/**
 * 条形码配置查询条件接口
 */
export interface BarcodeConfigQuery extends PageQuery {
  /** 规则编码 */
  configCode?: string;
  /** 规则名称 */
  configName?: string;
  /** 业务类型 */
  bizType?: string;
  /** 物料类别ID */
  categoryId?: number;
  /** 启用状态 */
  enableFlag?: number;
}

/**
 * 条形码配置视图对象接口
 */
export interface BarcodeConfigVO {
  /** 配置ID */
  configId: number;
  /** 配置编码 */
  configCode: string;
  /** 配置名称 */
  configName: string;
  /** 前缀 */
  prefix?: string;
  /** 日期格式 */
  datePattern?: string;
  /** 流水长度 */
  sequenceLength: number;
  /** 当前流水号（后端 WmBarcodeConfigVo.currentSequence，改规则前判断是否撞号的依据） */
  currentSequence?: number;
  /** 校验位 */
  withCheckDigit?: number;
  /** 业务类型 */
  bizType?: string;
  /** 物料类别ID */
  categoryId?: number;
  /** 启用状态 */
  enableFlag?: number;
  /** 备注 */
  remark?: string;
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
}

/**
 * 条形码配置表单接口
 */
export interface BarcodeConfigForm {
  /** 配置ID */
  configId?: number;
  /** 配置编码 */
  configCode: string;
  /** 配置名称 */
  configName: string;
  /** 前缀 */
  prefix?: string;
  /** 日期格式 */
  datePattern?: string;
  /** 流水长度 */
  sequenceLength: number;
  /** 当前流水号（后端 WmBarcodeConfigVo.currentSequence，改规则前判断是否撞号的依据） */
  currentSequence?: number;
  /** 校验位 */
  withCheckDigit?: number;
  /** 业务类型 */
  bizType?: string;
  /** 物料类别ID */
  categoryId?: number;
  /** 启用状态 */
  enableFlag?: number;
  /** 备注 */
  remark?: string;
}

/**
 * 条形码查询条件接口
 */
export interface BarcodeQuery extends PageQuery {
  /** 条码 */
  barcodeCode?: string;
  /** 配置ID */
  configId?: number;
  /** 业务类型 */
  bizType?: string;
  /** 业务ID */
  bizId?: number;
  /** 物料ID */
  materialId?: number;
  /** 库存ID */
  stockId?: number;
  /** 状态 */
  status?: string;
}

/**
 * 条形码视图对象接口
 */
export interface BarcodeVO {
  /** 条码ID */
  barcodeId: number;
  /** 条码 */
  barcodeCode: string;
  /** 配置ID */
  configId?: number;
  /** 配置编码 */
  configCode?: string;
  /** 业务类型 */
  bizType?: string;
  /** 业务ID */
  bizId?: number;
  /** 业务行ID */
  bizLineId?: number;
  /** 物料ID */
  materialId?: number;
  /** 批次ID */
  batchId?: number;
  /** 库存ID */
  stockId?: number;
  /** 状态 */
  status?: string;
  /** 生成时间 */
  generateTime?: string;
  /** 绑定时间 */
  bindTime?: string;
  /** 过期时间 */
  expireTime?: string;
  /** 备注 */
  remark?: string;
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
}

/**
 * 条形码表单接口
 */
export interface BarcodeForm {
  /** 条码ID */
  barcodeId?: number;
  /** 条码 */
  barcodeCode: string;
  /** 配置ID */
  configId?: number;
  /** 配置编码 */
  configCode?: string;
  /** 业务类型 */
  bizType?: string;
  /** 业务ID */
  bizId?: number;
  /** 业务行ID */
  bizLineId?: number;
  /** 物料ID */
  materialId?: number;
  /** 批次ID */
  batchId?: number;
  /** 库存ID */
  stockId?: number;
  /** 状态 */
  status?: string;
  /** 生成时间 */
  generateTime?: string;
  /** 绑定时间 */
  bindTime?: string;
  /** 过期时间 */
  expireTime?: string;
  /** 备注 */
  remark?: string;
}
