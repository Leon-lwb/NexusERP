export interface TeamScheduleQuery extends PageQuery {
  planId?: number;
  teamId?: number;
  shiftId?: number;
  scheduledDate?: string;
}

export interface TeamScheduleVO {
  teamScheduleId: number;
  planId: number;
  teamId: number;
  shiftId: number;
  scheduledDate: string;
  weekDay: number;
  isHoliday: number;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface TeamScheduleForm {
  teamScheduleId?: number;
  planId?: number;
  teamId?: number;
  shiftId?: number;
  scheduledDate?: string;
  weekDay?: number;
  isHoliday?: number;
  remark?: string;
}
