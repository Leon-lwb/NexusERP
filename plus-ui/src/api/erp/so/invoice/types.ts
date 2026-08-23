/**
 * 销售发票查询参数
 */
export interface InvoiceQuery extends PageQuery {
  /** 发票号 */
  invoiceNo?: string;
  /** 客户ID */
  clientId?: number;
  /** 客户名称 */
  clientName?: string;
  /** 发票类型 */
  invoiceType?: string;
  /** 发票状态 */
  status?: string;
  /** 销售订单ID */
  soId?: number;
  /** 合同ID */
  contractId?: number;
}

/**
 * 销售发票视图对象
 */
export interface InvoiceVO {
  /** 发票ID */
  invoiceId: number;
  /** 发票号 */
  invoiceNo: string;
  /** 发票代码 */
  invoiceCode?: string;
  /** 发票类型 */
  invoiceType: string;
  /** 客户ID */
  clientId: number;
  /** 客户名称 */
  clientName: string;
  /** 购买方纳税人识别号 */
  buyerTaxId?: string;
  /** 购买方发票抬头 */
  buyerTitle?: string;
  /** 购买方开户行 */
  buyerBankName?: string;
  /** 购买方银行账号 */
  buyerBankAccount?: string;
  /** 购买方地址、电话 */
  buyerAddressPhone?: string;
  /** 销售订单ID */
  soId: number;
  /** 合同ID */
  contractId: number;
  /** 开票日期 */
  invoiceDate: string;
  /** 币种 */
  currency: string;
  /** 金额（不含税） */
  amount: number;
  /** 税额 */
  taxAmount: number;
  /** 价税合计 */
  totalAmount: number;
  /** 已核销金额 */
  verifiedAmount: number;
  /** 发票状态 */
  status: string;
  /** 开票渠道 MANUAL-手工 ELECTRONIC-电子发票平台 */
  issueChannel?: string;
  /** 备注 */
  remark: string;
  /** 创建时间 */
  createTime: string;
  /** 更新时间 */
  updateTime: string;
  /** 发票明细行列表（详情返回） */
  soInvoiceLineList?: InvoiceLineForm[];
}

/**
 * 销售发票表单对象
 */
export interface InvoiceForm {
  /** 发票ID */
  invoiceId?: number;
  /** 发票号 */
  invoiceNo: string;
  /** 发票代码 */
  invoiceCode?: string;
  /** 发票类型 */
  invoiceType?: string;
  /** 客户ID */
  clientId?: number;
  /** 客户名称 */
  clientName?: string;
  /** 购买方纳税人识别号 */
  buyerTaxId?: string;
  /** 购买方发票抬头 */
  buyerTitle?: string;
  /** 购买方开户行 */
  buyerBankName?: string;
  /** 购买方银行账号 */
  buyerBankAccount?: string;
  /** 购买方地址、电话 */
  buyerAddressPhone?: string;
  /** 销售订单ID */
  soId?: number;
  /** 合同ID */
  contractId?: number;
  /** 开票日期 */
  invoiceDate?: string;
  /** 币种 */
  currency?: string;
  /** 汇率 */
  exchangeRate?: number;
  /** 金额（不含税） */
  amount?: number;
  /** 税额 */
  taxAmount?: number;
  /** 价税合计 */
  totalAmount?: number;
  /** 已核销金额 */
  verifiedAmount?: number;
  /** 发票状态 */
  status?: string;
  /** 开票渠道 MANUAL-手工 ELECTRONIC-电子发票平台 */
  issueChannel?: string;
  /** 备注 */
  remark?: string;
  /** 发票明细行列表 */
  soInvoiceLineList?: InvoiceLineForm[];
}

/**
 * 销售发票明细行表单对象
 */
export interface InvoiceLineForm {
  /** 发票行ID */
  lineId?: number;
  /** 发票ID */
  invoiceId?: number;
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
  /** 行金额（不含税） */
  lineAmount?: number;
  /** 税率 */
  taxRate?: number;
  /** 税额 */
  taxAmount?: number;
  /** 价税合计 */
  totalAmount?: number;
  /** 销售订单行ID */
  soLineId?: number;
  /** 合同行ID */
  contractLineId?: number;
  /** 排序 */
  sortOrder?: number;
  /** 备注 */
  remark?: string;
}
