/**
 * 部门请购单查询参数
 */
export interface RequisitionQuery extends PageQuery {
  /** 请购单号 */
  requisitionNo?: string;
  /** 部门ID */
  deptId?: number;
  /** 部门 */
  deptName?: string;
  /** 申请人ID */
  applicantId?: number;
  /** 申请人 */
  applicantName?: string;
  /** 状态 */
  status?: string;
}

/**
 * 部门请购单视图对象
 */
export interface RequisitionVO {
  /** 请购单ID */
  requisitionId: number;
  /** 请购单号 */
  requisitionNo: string;
  /** 状态 */
  status: string;
  /** 部门ID */
  deptId: number;
  /** 部门 */
  deptName: string;
  /** 申请人ID */
  applicantId: number;
  /** 申请人 */
  applicantName: string;
  /** 需求日期 */
  requiredDate: string;
  /** 备注 */
  remark: string;
  /** 创建时间 */
  createTime: string;
  /** 更新时间 */
  updateTime: string;
  /** 明细列表 */
  poRequisitionDetailList: RequisitionDetailVO[];
}

/**
 * 部门请购单明细视图对象
 */
export interface RequisitionDetailVO {
  /** 明细ID */
  detailId: number;
  /** 请购单ID */
  requisitionId: number;
  /** 物料ID */
  materialId: number;
  /** 物料编码 */
  materialCode: string;
  /** 物料名称 */
  materialName: string;
  /** 物料规格 */
  materialSpec: string;
  /** 单位 */
  unit: string;
  /** 需求数量 */
  requiredQuantity: number;
  /** 已发料数量 */
  issuedQuantity: number;
  /** 库存数量 */
  stockQuantity: number;
  /** 缺料数量 */
  shortageQuantity: number;
  /** 需求日期 */
  requiredDate: string;
  /** 状态 */
  status: string;
  /** 来源类型 */
  sourceType: string;
  /** 备注 */
  remark: string;
}

/**
 * 部门请购单表单对象
 */
export interface RequisitionForm {
  /** 请购单ID */
  requisitionId?: number;
  /** 请购单号 */
  requisitionNo: string;
  /** 状态 */
  status?: string;
  /** 部门ID */
  deptId?: number;
  /** 部门 */
  deptName?: string;
  /** 申请人ID */
  applicantId?: number;
  /** 申请人 */
  applicantName?: string;
  /** 需求日期 */
  requiredDate?: string;
  /** 备注 */
  remark?: string;
  /** 明细列表 */
  poRequisitionDetailList?: RequisitionDetailForm[];
}

/**
 * 部门请购单明细表单对象
 */
export interface RequisitionDetailForm {
  /** 明细ID */
  detailId?: number;
  /** 请购单ID */
  requisitionId?: number;
  /** 物料ID */
  materialId?: number;
  /** 物料编码 */
  materialCode?: string;
  /** 物料名称 */
  materialName?: string;
  /** 物料规格 */
  materialSpec?: string;
  /** 单位 */
  unit?: string;
  /** 需求数量 */
  requiredQuantity?: number;
  /** 已发料数量 */
  issuedQuantity?: number;
  /** 库存数量 */
  stockQuantity?: number;
  /** 缺料数量 */
  shortageQuantity?: number;
  /** 需求日期 */
  requiredDate?: string;
  /** 状态 */
  status?: string;
  /** 来源类型 */
  sourceType?: string;
  /** 备注 */
  remark?: string;
}
