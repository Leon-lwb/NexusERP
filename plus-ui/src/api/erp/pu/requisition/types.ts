/**
 * 采购请购单视图对象接口
 * 定义了采购请购单的详细属性和字段
 */
export interface PurchaseRequisitionVO {
  /** 请购单ID */
  requisitionId?: string | number;
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
  /** 乐观锁版本；NULL 或非法数值绝不能按 0 处理。 */
  version?: number | null;
  /** 原 AI 决策运行 ID，雪花 ID 必须保留精确字符串。 */
  decisionRunId?: string | number | null;
  /** 原 AI 决策动作 ID。 */
  decisionActionId?: string | number | null;
  /** 原认证命令的租户内幂等键；仅作候选提示，不证明永久 command 存在。 */
  decisionIdempotencyKey?: string | null;
  /** 已绑定的 Flowable 审批流；绑定时禁止直接认证状态转换。 */
  selectFlowId?: string | number | null;
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

/** 只读查询权限下的实际主体及 UI 权限提示；布尔值不签发写授权。 */
export interface CertifiedRequisitionWorkflowContext {
  tenantId: string;
  userId: string | number;
  approveAllowed: boolean;
  convertAllowed: boolean;
}

/** 服务端分配账本事件原值，仅用于展示，不由前端计算数量或业务事实。 */
export interface CertifiedRequisitionWorkflowAllocation {
  eventId: string | number;
  allocationId: string | number;
  operation: string;
  fromStatus: string;
  toStatus: string;
  occupiedBefore: string | number;
  occupiedAfter: string | number;
  requisitionLineId: string | number | null;
  orderLineId: string | number | null;
  actorId: string | number;
  replayed: boolean;
}

/** 认证批准/转单的整单结果，与旧可信创建的 Result 保持独立。 */
export interface CertifiedRequisitionWorkflowResult {
  requisitionId: string | number;
  requisitionNo: string;
  status: string;
  version: number;
  orderId: string | number | null;
  orderNo: string | null;
  replayed: boolean;
  allocations: CertifiedRequisitionWorkflowAllocation[];
}
