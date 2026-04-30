export interface ShiftQuery extends PageQuery {
  shiftCode?: string;
  shiftName?: string;
  planId?: number;
  shiftCategory?: string;
}

export interface ShiftVO {
  shiftId: number;
  shiftCode: string;
  shiftName: string;
  planId: number;
  startTime: string;
  endTime: string;
  workHours: number;
  isCrossDay: number;
  restStartTime: string;
  restEndTime: string;
  lateThreshold: number;
  earlyLeaveThreshold: number;
  shiftCategory: string;
  color: string;
  sortOrder: number;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface ShiftForm {
  shiftId?: number;
  shiftCode?: string;
  shiftName?: string;
  planId?: number;
  startTime?: string;
  endTime?: string;
  workHours?: number;
  isCrossDay?: number;
  restStartTime?: string;
  restEndTime?: string;
  lateThreshold?: number;
  earlyLeaveThreshold?: number;
  shiftCategory?: string;
  color?: string;
  sortOrder?: number;
  remark?: string;
}
