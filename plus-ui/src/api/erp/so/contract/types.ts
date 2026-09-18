/**
 * 销售合同查询参数
 */
export interface ContractQuery extends PageQuery {
  /** 合同编号 */
  contractNo?: string;
  /** 合同名称 */
  contractName?: string;
  /** 客户ID */
  clientId?: number;
  /** 客户名称 */
  clientName?: string;
  /** 合同类型 */
  contractType?: string;
  /** 合同状态 */
  status?: string;
}

/**
 * 销售合同视图对象
 */
export interface ContractVO {
  /** 合同ID */
  contractId: number;
  /** 合同编号 */
  contractNo: string;
  /** 合同名称 */
  contractName: string;
  /** 合同类型 */
  contractType: string;
  /** 客户ID */
  clientId: number;
  /** 客户名称 */
  clientName: string;
  /** 签订日期 */
  signDate: string;
  /** 生效日期 */
  effectiveDate: string;
  /** 失效日期 */
  expiryDate: string;
  /** 合同金额 */
  totalAmount: number;
  /** 币种 */
  currency: string;
  /** 付款条款 */
  paymentTerms: string;
  /** 销售代表ID */
  salesRepId: number;
  /** 销售代表名称 */
  salesRepName: string;
  /** 合同状态 */
  status: string;
  /**
   * 正文签署状态（来自 es_sign_document.sign_status：0未签 1部分签 2已签）
   * <p>so_contract 表没有这一列，由后端列表查询批量补充；该合同尚无正文时为 null。</p>
   */
  docSignStatus?: string;
  /** 备注 */
  remark: string;
  /** 创建时间 */
  createTime: string;
  /** 更新时间 */
  updateTime: string;
  /** 合同明细行列表（详情返回） */
  soContractLineList?: ContractLineForm[];
}

/**
 * 销售合同表单对象
 */
export interface ContractForm {
  /** 合同ID */
  contractId?: number;
  /** 合同编号 */
  contractNo: string;
  /** 合同名称 */
  contractName: string;
  /** 合同类型 */
  contractType?: string;
  /** 客户ID */
  clientId?: number;
  /** 客户名称 */
  clientName?: string;
  /** 签订日期 */
  signDate?: string;
  /** 生效日期 */
  effectiveDate?: string;
  /** 失效日期 */
  expiryDate?: string;
  /** 合同金额 */
  totalAmount?: number;
  /** 币种 */
  currency?: string;
  /** 付款条款 */
  paymentTerms?: string;
  /** 销售代表ID */
  salesRepId?: number;
  /** 销售代表名称 */
  salesRepName?: string;
  /** 合同状态 */
  status?: string;
  /** 审批流程ID */
  selectFlowId?: number;
  /** 备注 */
  remark?: string;
  /** 合同明细行列表 */
  soContractLineList?: ContractLineForm[];
}

/**
 * 销售合同明细行表单对象
 */
export interface ContractLineForm {
  /** 合同行ID */
  lineId?: number;
  /** 合同ID */
  contractId?: number;
  /** 行号 */
  lineNo?: string;
  /** 物料ID */
  materialId?: number;
  /** 物料编码 */
  materialCode?: string;
  /** 物料名称 */
  materialName?: string;
  /** 规格型号 */
  materialSpec?: string;
  /** 数量 */
  quantity?: number;
  /** 单位 */
  unit?: string;
  /** 单价 */
  unitPrice?: number;
  /** 行金额 */
  lineAmount?: number;
  /** 税率 */
  taxRate?: number;
  /** 税额 */
  taxAmount?: number;
  /** 交货日期 */
  deliveryDate?: string;
  /** 行状态 */
  lineStatus?: string;
  /** 排序 */
  sortOrder?: number;
  /** 备注 */
  remark?: string;
}
