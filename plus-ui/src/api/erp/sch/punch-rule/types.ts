export interface PunchRuleQuery extends PageQuery {
  ruleName?: string;
  shiftName?: string;
  isEnabled?: number;
}

export interface PunchRuleVO {
  ruleId: number;
  ruleName: string;
  shiftId: number;
  shiftName: string;
  workStartTime: string;
  workEndTime: string;
  allowClockInStart: string;
  allowClockInEnd: string;
  allowClockOutStart: string;
  allowClockOutEnd: string;
  locationLat: number;
  locationLng: number;
  locationRadius: number;
  locationAddress: string;
  allowWifiSsids: string;
  allowBluetoothDevices: string;
  allowOutdoorPunch: number;
  outdoorRequirePhoto: number;
  allowSupplement: number;
  supplementDeadline: number;
  lateThreshold: number;
  earlyLeaveThreshold: number;
  absentThreshold: number;
  isEnabled: number;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface PunchRuleForm {
  ruleId?: number;
  ruleName?: string;
  shiftId?: number;
  shiftName?: string;
  workStartTime?: string;
  workEndTime?: string;
  allowClockInStart?: string;
  allowClockInEnd?: string;
  allowClockOutStart?: string;
  allowClockOutEnd?: string;
  locationLat?: number;
  locationLng?: number;
  locationRadius?: number;
  locationAddress?: string;
  allowWifiSsids?: string;
  allowBluetoothDevices?: string;
  allowOutdoorPunch?: number;
  outdoorRequirePhoto?: number;
  allowSupplement?: number;
  supplementDeadline?: number;
  lateThreshold?: number;
  earlyLeaveThreshold?: number;
  absentThreshold?: number;
  isEnabled?: number;
  remark?: string;
}
