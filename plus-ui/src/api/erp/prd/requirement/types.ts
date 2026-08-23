export interface RequirementQuery extends PageQuery {
  requirementNo?: string;
  planNo?: string;
  status?: string;
}

/** 物料需求明细 */
export interface RequirementDetailVO {
  detailId?: number | string;
  requirementId?: number | string;
  materialId?: number | string;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  unit?: string;
  bomQuantity?: number | string;
  lossRate?: number | string;
  requiredQuantity?: number | string;
  issuedQuantity?: number | string;
  stockQuantity?: number | string;
  shortageQuantity?: number | string;
  operationStep?: string;
  workstationId?: number | string;
  workstationName?: string;
  leadTime?: number | string;
  requiredDate?: string;
  status?: string;
  remark?: string;
  sourceType?: string;
  index?: number;
}

export interface RequirementVO {
  requirementId: number;
  requirementNo: string;
  planId: number;
  planNo: string;
  bomId: number;
  requirementDate: string;
  totalMaterialTypes: number;
  totalRequiredQuantity: number;
  status: string;
  plannerId: number;
  plannerName: string;
  deptId: number;
  deptName: string;
  confirmedTime: string;
  issuedTime: string;
  closedTime: string;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  remark: string;
  sourceType?: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
  prdMaterialRequirementDetailList?: RequirementDetailVO[];
}

export interface RequirementForm {
  requirementId?: number;
  requirementNo?: string;
  planId?: number;
  planNo?: string;
  bomId?: number;
  requirementDate?: string;
  totalMaterialTypes?: number;
  totalRequiredQuantity?: number;
  status?: string;
  plannerId?: number;
  plannerName?: string;
  deptId?: number;
  deptName?: string;
  confirmedTime?: string;
  issuedTime?: string;
  closedTime?: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  remark?: string;
  sourceType?: string;
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
  [key: string]: any;
}
