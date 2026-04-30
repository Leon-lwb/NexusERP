export interface PlanteamQuery extends PageQuery {
  planId?: number;
  teamId?: number;
  teamName?: string;
}

export interface PlanteamVO {
  recordId: number;
  planId: number;
  teamId: number;
  teamName: string;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface PlanteamForm {
  recordId?: number;
  planId?: number;
  teamId?: number;
  teamName?: string;
  remark?: string;
}
