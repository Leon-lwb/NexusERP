/**
 * 采购进项发票查询参数
 */
export interface InvoiceQuery extends PageQuery {
  /** 发票号 */
  invoiceNo?: string;
  /** 供应商ID */
  vendorId?: number;
  /** 供应商名称 */
  vendorName?: string;
  /** 发票类型 */
  invoiceType?: string;
  /** 发票状态 */
  status?: string;
  /** 认证状态 */
  certStatus?: string;
  /** 采购订单ID */
  poId?: number;
}

/**
 * 采购进项发票视图对象
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
  /** 供应商ID */
  vendorId?: number;
  /** 供应商名称 */
  vendorName?: string;
  /** 销售方纳税人识别号 */
  sellerTaxId?: string;
  /** 销售方发票抬头 */
  sellerTitle?: string;
  /** 销售方开户行 */
  sellerBankName?: string;
  /** 销售方银行账号 */
  sellerBankAccount?: string;
  /** 销售方地址、电话 */
  sellerAddressPhone?: string;
  /** 采购订单ID */
  poId?: number;
  /** 采购合同ID */
  contractId?: number;
  /** 红冲原发票ID */
  originalInvoiceId?: number;
  /** 是否红字发票 0-否 1-是 */
  redFlag?: string;
  /** 开票日期 */
  invoiceDate?: string;
  /** 币种 */
  currency?: string;
  /** 金额（不含税） */
  amount?: number;
  /** 税额 */
  taxAmount?: number;
  /** 价税合计 */
  totalAmount?: number;
  /** 已核销金额 */
  verifiedAmount?: number;
  /** 发票状态 */
  status: string;
  /** 认证状态 */
  certStatus?: string;
  /** 认证日期 */
  certDate?: string;
  /** 可抵扣税额 */
  deductibleTaxAmount?: number;
  /** 来源渠道 */
  issueChannel?: string;
  /** 数电票号码 */
  eInvoiceNo?: string;
  /** 发票PDF的OSS对象ID */
  pdfOssId?: number;
  /** 发票PDF访问地址 */
  pdfUrl?: string;
  /** 发票OFD的OSS对象ID */
  ofdOssId?: number;
  /** 发票OFD访问地址 */
  ofdUrl?: string;
  /** 乐观锁版本号 */
  version?: number;
  /** 备注 */
  remark?: string;
  /** 创建时间 */
  createTime?: string;
  /** 更新时间 */
  updateTime?: string;
  /** 发票明细行列表 */
  puInvoiceLineList?: InvoiceLineForm[];
}

/**
 * 采购进项发票表单对象
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
  /** 供应商ID */
  vendorId?: number;
  /** 供应商名称 */
  vendorName?: string;
  /** 销售方纳税人识别号 */
  sellerTaxId?: string;
  /** 销售方发票抬头 */
  sellerTitle?: string;
  /** 销售方开户行 */
  sellerBankName?: string;
  /** 销售方银行账号 */
  sellerBankAccount?: string;
  /** 销售方地址、电话 */
  sellerAddressPhone?: string;
  /** 采购订单ID */
  poId?: number;
  /** 采购合同ID */
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
  /** 来源渠道 */
  issueChannel?: string;
  /** 备注 */
  remark?: string;
  /** 发票明细行列表 */
  puInvoiceLineList?: InvoiceLineForm[];
}

/**
 * 采购进项发票明细行表单对象
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
  /** 单价(不含税) */
  unitPrice?: number;
  /** 行金额(不含税) */
  lineAmount?: number;
  /** 税率 */
  taxRate?: number;
  /** 税额 */
  taxAmount?: number;
  /** 价税合计 */
  totalAmount?: number;
  /** 采购订单行ID */
  poLineId?: number;
  /** 收货单行ID(三单匹配用) */
  grLineId?: number;
  /** 采购合同行ID */
  contractLineId?: number;
  /** 排序 */
  sortOrder?: number;
  /** 备注 */
  remark?: string;
}

/**
 * 三单匹配(PO + 收货 + 发票)校验结果
 */
export interface MatchResult {
  /** 是否通过 */
  passed: boolean;
  /** 差异条目数 */
  mismatchCount: number;
  /** 差异明细 */
  mismatches: string[];
  /** 匹配汇总 */
  summary?: string;
}
