/**
 * 采购订单视图对象接口
 * 定义了采购订单的详细属性和字段
 */
export interface PurchaseOrderVO {
  /** 订单ID */
  orderId?: number;
  /** 订单编号 */
  orderNo?: string;
  /** 供应商ID */
  vendorId?: number;
  /** 供应商编码 */
  vendorCode?: string;
  /** 供应商名称 */
  vendorName?: string;
  /** 采购部门ID */
  deptId?: number;
  /** 采购部门名称 */
  deptName?: string;
  /** 采购员ID */
  buyerId?: number;
  /** 采购员名称 */
  buyerName?: string;
  /** 关联请购单ID */
  requisitionId?: number;
  /** 关联请购单号 */
  requisitionNo?: string;
  /** 订单日期 */
  orderDate?: string;
  /** 预计到货日期 */
  expectedArrivalDate?: string;
  /** 订单状态 */
  status?: string;
  /** 币种 */
  currency?: string;
  /** 汇率 */
  exchangeRate?: number;
  /** 总金额（价税合计，含运费） */
  totalAmount?: number;
  /** 税额合计 */
  taxAmount?: number;
  /** 运费（后端 PuPurchaseOrderBo/Vo/实体三层齐备，此前前端零命中） */
  freightAmount?: number;
  /** 备注 */
  remark?: string;
  /** 订单行列表 */
  lineList?: PurchaseOrderLineVO[];
}

/**
 * 采购订单查询条件接口
 * 定义了搜索采购订单时使用的筛选参数
 */
export interface PurchaseOrderQuery {
  /** 当前页码 */
  pageNum?: number;
  /** 每页显示数量 */
  pageSize?: number;
  /** 订单编号 */
  orderNo?: string;
  /** 供应商名称 */
  vendorName?: string;
  /** 采购部门ID */
  deptId?: number;
  /** 采购员ID */
  buyerId?: number;
  /** 订单状态 */
  status?: string;
}

/**
 * 采购订单表单接口
 * 继承自PurchaseOrderVO，用于新增和编辑操作
 */
export interface PurchaseOrderForm extends PurchaseOrderVO {}

export interface PurchaseOrderLineVO {
  lineId?: number;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  unit?: string;
  unitName?: string;
  orderQuantity?: number;
  receivedQuantity?: number;
  /**
   * 已退货数量（ERP-PU-05，后端 PuPurchaseOrderLine.returnedQuantity 已随
   * PuPurchaseOrderVo.lineList 返回）。净采购量 = orderQuantity - returnedQuantity。
   */
  returnedQuantity?: number;
  receiveStatus?: string;
  unitPrice?: number;
  /** 不含税单价 */
  unitPriceExcludingTax?: number;
  taxRate?: number;
  /** 行金额（含税） */
  lineAmount?: number;
  /** 行税额 */
  lineTaxAmount?: number;
  /** 不含税行金额 */
  lineAmountExcludingTax?: number;
  sortOrder?: number;
}

/** 认证订单当前真实主体；各布尔值只是页面提示，写资格仍由服务端重验。 */
export interface CertifiedOrderContext {
  tenantId: string;
  userId: string | number;
  termsAddAllowed: boolean;
  termsConfirmAllowed: boolean;
  approveAllowed: boolean;
  closeAllowed: boolean;
  cancelAllowed: boolean;
  priceVisible: boolean;
}

/** 原生版本和来源字段；NULL历史版本绝不默认为0。 */
export interface CertifiedOrderHead {
  tenantId: string;
  orderId: string | number;
  orderNo: string;
  requisitionId: string | number | null;
  vendorId: string | number | null;
  status: string;
  version: number | null;
  quoteId: string | number | null;
  selectFlowId: string | number | null;
  requisitionSourceType: string | null;
  decisionRunId: string | number | null;
  decisionActionId: string | number | null;
  sourceRequestKey: string | null;
  originalCommandPresent: boolean | null;
}

/** 原生不可变报价展示；金额/数量全部十进制文本，页面不重算税额或供应KPI。 */
export interface CertifiedOrderQuote {
  quoteId: string | number;
  orderId: string | number;
  quoteVersion: number;
  enteredBy: string | number;
  requestKey: string;
  quoteHash: string;
  quantity: string;
  unitPriceExcludingTax: string;
  taxRate: string;
  currency: string;
  exchangeRate: string;
  promisedArrivalDate: string;
  validFrom: string;
  validUntil: string;
  supplierQuoteReference: string;
  supplierContact: string;
  evidenceReference: string;
  evidenceSha256: string;
  paymentTerms: string;
  deliveryTerms: string;
  confirmationId: string | number | null;
  confirmedBy: string | number | null;
  confirmationHash: string | null;
  reviewNote: string | null;
}

export interface CertifiedOrderAllocation {
  allocationId: string | number;
  requisitionLineId: string | number;
  orderLineId: string | number;
  allocationStatus: string;
  version: number | null;
}

export interface CertifiedOrderDetail {
  order: CertifiedOrderHead;
  quotes: CertifiedOrderQuote[];
  allocations: CertifiedOrderAllocation[];
  priceVisible: boolean;
  serverDate: string;
  genericEditAllowedHint: boolean;
}

/** 人工真实供应条款，不含服务端身份、数量、版本或认证字段。 */
export interface SupplierOrderTermsInput {
  unitPriceExcludingTax: string;
  taxRate: string;
  currency: string;
  exchangeRate: string;
  promisedArrivalDate: string;
  validFrom: string;
  validUntil: string;
  supplierQuoteReference: string;
  supplierContact: string;
  evidenceReference: string;
  evidenceSha256: string;
  paymentTerms: string;
  deliveryTerms: string;
}
