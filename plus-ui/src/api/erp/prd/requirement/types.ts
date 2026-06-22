export interface RequirementQuery extends PageQuery {
  requirementNo?: string;
  planNo?: string;
  status?: string;
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
  departmentId: number;
  departmentName: string;
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
  departmentId?: number;
  departmentName?: string;
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