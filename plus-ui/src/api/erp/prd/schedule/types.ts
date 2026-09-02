export interface SchedulePlanVO {
  planId: string;
  planNo: string;
  planName: string;
  workshopId: string;
  workshopName: string;
  startDate: string;
  horizonDays: number;
  strategy: string;
  woCount: number;
  taskCount: number;
  skippedCount: number;
  totalHours: number;
  delayedCount: number;
  makespanDate: string;
  utilizationJson: string;
  status: string;
  remark: string;
  createTime: string;
}

export interface SchedulePlanQuery extends PageQuery {
  planNo?: string;
  planName?: string;
  strategy?: string;
  status?: string;
  workshopId?: string;
}

export interface ScheduleRunForm {
  startDate: string;
  horizonDays: number;
  strategy: string;
  workshopId?: string;
  workshopName?: string;
  planName?: string;
}

export interface ScheduleTaskVO {
  taskId: string;
  planId: string;
  woId: string;
  woNo: string;
  materialCode: string;
  materialName: string;
  seqNum: number;
  stepId: string;
  processCode: string;
  processName: string;
  resourceType: string;
  resourceId: string;
  resourceName: string;
  setupHours: number;
  processHours: number;
  startTime: string;
  endTime: string;
  durationDays: number;
  delayFlag: number;
}

export interface GanttBar {
  taskId: string;
  seqNum: number;
  processName: string;
  resourceType: string;
  resourceId: string;
  resourceName: string;
  hours: number;
  startTime: string;
  endTime: string;
  durationDays: number;
}

export interface GanttRow {
  woId: string;
  woNo: string;
  materialCode: string;
  materialName: string;
  dueDate: string;
  delay: boolean;
  bars: GanttBar[];
}

export interface ResourceRate {
  resourceKey: string;
  resourceName: string;
  bookedHours: number;
  availableHours: number;
  rate: number;
}

export interface ScheduleGanttVO {
  planId: string;
  planNo: string;
  strategy: string;
  startDate: string;
  horizonDays: number;
  woCount: number;
  delayedCount: number;
  skippedCount: number;
  totalHours: number;
  rows: GanttRow[];
  loads: ResourceRate[];
}
