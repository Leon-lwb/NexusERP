export interface CapacityCalendarVO {
  calendarId: number;
  tenantId: number;
  calendarDate: string;
  objectType: string;
  objectId: number;
  objectCode: string;
  objectName: string;
  shiftId: number;
  shiftCode: string;
  shiftName: string;
  availableHours: number;
  plannedHours: number;
  actualHours: number;
  efficiency: number;
  effectiveHours: number;
  remainingHours: number;
  status: string;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface CapacityCalendarQuery extends PageQuery {
  calendarDate?: string;
  objectType?: string;
  objectId?: number;
  objectCode?: string;
  objectName?: string;
  shiftId?: number;
  status?: string;
}

export interface CapacityCalendarForm {
  calendarId?: number;
  calendarDate?: string;
  objectType?: string;
  objectId?: number;
  objectCode?: string;
  objectName?: string;
  shiftId?: number;
  availableHours?: number;
  efficiency?: number;
  status?: string;
  remark?: string;
}
