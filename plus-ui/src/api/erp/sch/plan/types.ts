export interface SchPlanQuery extends PageQuery {
  planCode?: string;
  planName?: string;
  periodType?: string;
  status?: string;
}

export interface SchPlanVO {
  planId: number;
  planCode: string;
  planName: string;
  startDate: string;
  endDate: string;
  periodType: string;
  rotationType: number;
  status: string;
  publishedBy: string;
  publishedTime: string;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface SchPlanForm {
  planId?: number;
  planCode?: string;
  planName?: string;
  startDate?: string;
  endDate?: string;
  periodType?: string;
  rotationType?: number;
  status?: string;
  publishedBy?: string;
  publishedTime?: string;
  remark?: string;
}
