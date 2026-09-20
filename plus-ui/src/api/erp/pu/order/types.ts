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
