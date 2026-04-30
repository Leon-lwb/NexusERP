export interface TeamQuery extends PageQuery {
  teamCode?: string;
  teamName?: string;
  teamCategory?: string;
  workshopName?: string;
  status?: number;
}

export interface TeamVO {
  teamId: number;
  teamCode: string;
  teamName: string;
  teamCategory: string;
  workshopId: number;
  workshopName: string;
  leaderId: number;
  leaderName: string;
  memberCount: number;
  status: number;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface TeamForm {
  teamId?: number;
  teamCode?: string;
  teamName?: string;
  teamCategory?: string;
  workshopId?: number;
  workshopName?: string;
  leaderId?: number;
  leaderName?: string;
  memberCount?: number;
  status?: number;
  remark?: string;
}
