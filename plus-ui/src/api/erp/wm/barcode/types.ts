/** 条码主对象只引用现有身份；NULL历史映射保留为旧来源，不推造类型。 */
export type BarcodePrimaryObjectType = 'SN' | 'BATCH' | 'MATERIAL' | 'DOCUMENT';

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
  categoryId?: string;
  /** 启用状态 */
  enableFlag?: number;
}

/**
 * 条形码配置视图对象接口
 */
export interface BarcodeConfigVO {
  /** 配置ID */
  configId: string;
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
  categoryId?: string;
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
  configId?: string;
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
  categoryId?: string;
  /** 启用状态 */
  enableFlag?: number;
  /** 备注 */
  remark?: string;
}

/**
 * 条形码查询条件接口
 */
export interface BarcodeQuery extends PageQuery {
  configCode?: string;
  /** 主对象类型；历史NULL或缺省只显示原来源。 */
  primaryObjectType?: BarcodePrimaryObjectType | null;
  /** 正式ERP SN身份ID，不使用MES本地SN ID。 */
  snId?: string;

  /** 条码 */
  barcodeCode?: string;
  /** 配置ID */
  configId?: string;
  /** 业务类型 */
  bizType?: string;
  /** 业务ID */
  bizId?: string;
  /** 物料ID */
  materialId?: string;
  /** 库存ID */
  stockId?: string;
  /** 状态 */
  status?: string;
}

/**
 * 条形码视图对象接口
 */
export interface BarcodeVO {
  /** 主对象类型；历史NULL或缺省只显示原来源。 */
  primaryObjectType?: BarcodePrimaryObjectType | null;
  /** 正式ERP SN身份ID，不使用MES本地SN ID。 */
  snId?: string;

  /** 条码ID */
  barcodeId: string;
  /** 条码 */
  barcodeCode: string;
  /** 配置ID */
  configId?: string;
  /** 配置编码 */
  configCode?: string;
  /** 业务类型 */
  bizType?: string;
  /** 业务ID */
  bizId?: string;
  /** 业务行ID */
  bizLineId?: string;
  /** 物料ID */
  materialId?: string;
  /** 批次ID */
  batchId?: string;
  /** 原批次编码上下文，创建后不可重绑。 */
  batchCode?: string;
  /** 库存ID */
  stockId?: string;
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
  /** 主对象类型；历史NULL或缺省只显示原来源。 */
  primaryObjectType?: BarcodePrimaryObjectType | null;
  /** 正式ERP SN身份ID，不使用MES本地SN ID。 */
  snId?: string;

  /** 条码ID */
  barcodeId?: string;
  /** 条码 */
  barcodeCode: string;
  /** 配置ID */
  configId?: string;
  /** 配置编码 */
  configCode?: string;
  /** 业务类型 */
  bizType?: string;
  /** 业务ID */
  bizId?: string;
  /** 业务行ID */
  bizLineId?: string;
  /** 物料ID */
  materialId?: string;
  /** 批次ID */
  batchId?: string;
  /** 原批次编码上下文，创建后不可重绑。 */
  batchCode?: string;
  /** 库存ID */
  stockId?: string;
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
