/**
 * 采购请购单视图对象接口
 * 定义了采购请购单的详细属性和字段
 */
export interface PurchaseRequisitionVO {
  /** 请购单ID */
  requisitionId?: number;
  /** 请购单编号 */
  requisitionNo?: string;
  /** 请购单名称 */
  requisitionName?: string;
  /** 申请人ID */
  applicantId?: number;
  /** 申请人姓名 */
  applicantName?: string;
  /** 请购日期 */
  requisitionDate?: string;
  /** 要求到货日期 */
  requiredDate?: string;
  /** 来源类型 */
  sourceType?: string;
  /** 请购单状态 */
  status?: string;
  /** 备注 */
  remark?: string;
  /** 请购单行项列表 */
  puPurchaseRequisitionLineList?: any[];
}

/**
 * 采购请购单查询条件接口
 * 定义了搜索采购请购单时使用的筛选参数
 */
export interface PurchaseRequisitionQuery {
  /** 当前页码 */
  pageNum?: number;
  /** 每页显示数量 */
  pageSize?: number;
  /** 请购单编号 */
  requisitionNo?: string;
  /** 请购单名称 */
  requisitionName?: string;
  /** 请购单状态 */
  status?: string;
}

/**
 * 采购请购单表单接口
 * 继承自PurchaseRequisitionVO，用于新增和编辑操作
 */
export interface PurchaseRequisitionForm extends PurchaseRequisitionVO {}