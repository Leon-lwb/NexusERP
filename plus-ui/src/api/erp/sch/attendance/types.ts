export interface AttendanceQuery extends PageQuery {
  workerId?: number;
  workerName?: string;
  recordDate?: string;
  attendanceStatus?: string;
}

export interface AttendanceVO {
  attendanceId: number;
  workerId: number;
  workerName: string;
  recordDate: string;
  scheduleId: number;
  clockInTime: string;
  clockOutTime: string;
  lateMinutes: number;
  leaveEarlyMinutes: number;
  overtimeHours: number;
  attendanceStatus: string;
  sourceType: string;
  deviceId: string;
  deviceName: string;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface AttendanceForm {
  attendanceId?: number;
  workerId?: number;
  workerName?: string;
  recordDate?: string;
  scheduleId?: number;
  clockInTime?: string;
  clockOutTime?: string;
  lateMinutes?: number;
  leaveEarlyMinutes?: number;
  overtimeHours?: number;
  attendanceStatus?: string;
  sourceType?: string;
  deviceId?: string;
  deviceName?: string;
  remark?: string;
}
