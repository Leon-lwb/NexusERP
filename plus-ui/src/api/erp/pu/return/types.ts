/**
 * 采购退货单视图对象接口
 * 定义了采购退货单的详细属性和字段
 */
export interface PurchaseReturnVO {
  /** 退货单ID */
  returnId?: number;
  /** 退货单编号 */
  returnNo?: string;
  /** 关联订单ID */
  orderId?: number;
  /** 关联订单编号 */
  orderNo?: string;
  /** 供应商名称 */
  vendorName?: string;
  /** 退货日期 */
  returnDate?: string;
  /** 退货原因 */
  returnReason?: string;
  /** 退货总金额 */
  totalAmount?: number;
  /** 退货状态 */
  status?: string;
  /** 备注 */
  remark?: string;
  /** 退货单行项列表 */
  puPurchaseReturnLineList?: any[];
}

/**
 * 采购退货单查询条件接口
 * 定义了搜索采购退货单时使用的筛选参数
 */
export interface PurchaseReturnQuery {
  /** 当前页码 */
  pageNum?: number;
  /** 每页显示数量 */
  pageSize?: number;
  /** 退货单编号 */
  returnNo?: string;
  /** 关联订单编号 */
  orderNo?: string;
  /** 供应商名称 */
  vendorName?: string;
  /** 退货单状态 */
  status?: string;
}

/**
 * 采购退货单表单接口
 * 继承自PurchaseReturnVO，用于新增和编辑操作
 */
export interface PurchaseReturnForm extends PurchaseReturnVO {}