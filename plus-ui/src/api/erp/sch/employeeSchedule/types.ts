export interface EmployeeScheduleQuery extends PageQuery {
  workerName?: string;
  teamName?: string;
  shiftName?: string;
  scheduledDate?: string;
  scheduleStatus?: string;
}

export interface EmployeeScheduleVO {
  scheduleId: number;
  planId: number;
  workerId: number;
  workerName: string;
  teamId: number;
  teamName: string;
  shiftId: number;
  shiftName: string;
  scheduledDate: string;
  startTime: string;
  endTime: string;
  actualShiftId: number;
  scheduleStatus: string;
  isWorkingDay: number;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface EmployeeScheduleForm {
  scheduleId?: number;
  planId?: number;
  workerId?: number;
  workerName?: string;
  teamId?: number;
  teamName?: string;
  shiftId?: number;
  shiftName?: string;
  scheduledDate?: string;
  startTime?: string;
  endTime?: string;
  actualShiftId?: number;
  scheduleStatus?: string;
  isWorkingDay?: number;
  remark?: string;
}
