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
  /** 总金额 */
  totalAmount?: number;
  /** 备注 */
  remark?: string;
  /** 订单行列表 */
  lineList?: any[];
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
  /** 订单状态 */
  status?: string;
}

/**
 * 采购订单表单接口
 * 继承自PurchaseOrderVO，用于新增和编辑操作
 */
export interface PurchaseOrderForm extends PurchaseOrderVO {}