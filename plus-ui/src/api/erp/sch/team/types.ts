export interface TeamQuery extends PageQuery {
  teamCode?: string;
  teamName?: string;
  teamCategory?: string;
  workshopId?: number;
  workshopName?: string;
  memberCount?: number;
  status?: number;
}

/** 班组成员 */
export interface TeamMemberVO {
  memberId?: number;
  teamId?: number | string;
  workerId?: number | string;
  workerName?: string;
  workerCode?: string;
  role?: string;
  effectiveDate?: string;
  expireDate?: string;
  status?: string | number;
  index?: number;
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
  schTeamMemberList?: TeamMemberVO[];
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
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
  schTeamMemberList?: TeamMemberVO[];
}
