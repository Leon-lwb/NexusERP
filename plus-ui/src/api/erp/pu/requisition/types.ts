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
  /** 采购部门ID */
  deptId?: number;
  /** 采购部门名称 */
  deptName?: string;
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

/** 可信创建只接收真实来源身份；所有业务 ID 保持字符串。 */
export interface CertifiedPurchaseSource {
  materialId: string;
  vendorId: string;
  windowEnd: string;
  requirementDetailIds: readonly string[];
}

/** 与后端统一写命令对应，数量、单位、编号和租户不得由客户端提交。 */
export interface PuCertifiedPurchaseCommand extends CertifiedPurchaseSource {
  requestKey: string;
  expectedSourceFingerprint: string;
}

/** 数量只展示服务端原值，不在客户端换算或汇总。 */
export interface CertifiedPurchasePreviewLine {
  detailId: string;
  requiredDate: string;
  coveredDemandBase: string | number;
  orderQuantity: string | number;
  baseUomId: string;
  orderUomId: string;
}

export interface CertifiedPurchaseSourcePreview {
  materialId: string;
  vendorId: string;
  snapshotDate: string;
  windowEnd: string;
  sourceFingerprint: string;
  inventoryEvidence: string;
  catalog: {
    material: { materialId: string };
    baseUnit: { unitId: string };
    orderUnit: { unitId: string };
  };
  lines: CertifiedPurchasePreviewLine[];
}

export interface CertifiedPurchaseResult {
  requisitionId: string;
  requisitionNo: string;
  status: string;
  version: number;
  replayed: boolean;
  sourceFingerprint: string;
  lines: {
    lineId: string;
    requirementDetailId: string;
    materialId: string;
    baseUomId: string;
    applyQuantity: string | number;
    requiredDate: string;
  }[];
}

/** 保留业务信封，业务拒绝与网络结果未知不能混为成功。 */
export interface CertifiedPurchaseResponse<T> {
  code: number;
  msg?: string;
  data?: T;
}
