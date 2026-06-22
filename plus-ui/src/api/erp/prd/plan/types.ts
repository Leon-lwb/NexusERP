export interface PlanQuery extends PageQuery {
  planNo?: string;
  planName?: string;
  planType?: string;
  status?: string;
}

export interface PlanVO {
  planId: number;
  planNo: string;
  planName: string;
  version: number;
  originalPlanId: number;
  referenceNo: string;
  planType: string;
  planDate: string;
  startDate: string;
  endDate: string;
  status: string;
  priority: number;
  plannerId: number;
  plannerName: string;
  departmentId: number;
  departmentName: string;
  totalQuantity: number;
  confirmedTime: string;
  releasedTime: string;
  closedTime: string;
  bomName: string;
  bomVersion: string;
  isCurrent: number;
  bomId: number;
  attr4: number;
  materialRequirementId: number;
  materialRequirementNo: string;
  orderLineId: number;
  orderLineNo: string;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface PlanForm {
  planId?: number;
  planNo?: string;
  planName?: string;
  version?: number;
  originalPlanId?: number;
  referenceNo?: string;
  planType?: string;
  planDate?: string;
  startDate?: string;
  endDate?: string;
  status?: string;
  priority?: number;
  plannerId?: number;
  plannerName?: string;
  departmentId?: number;
  departmentName?: string;
  totalQuantity?: number;
  confirmedTime?: string;
  releasedTime?: string;
  closedTime?: string;
  bomName?: string;
  bomVersion?: string;
  isCurrent?: number;
  bomId?: number;
  attr4?: number;
  materialRequirementId?: number;
  materialRequirementNo?: string;
  orderLineId?: number;
  orderLineNo?: string;
  soLineId?: number;
  remark?: string;
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
  [key: string]: any;
}
