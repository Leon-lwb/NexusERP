export interface RoutingQuery extends PageQuery {
  routingCode?: string;
  routingName?: string;
  materialId?: number;
  isCurrent?: number;
  status?: number;
}

export interface RoutingVO {
  routingId: number;
  routingCode: string;
  routingName: string;
  routingDesc: string;
  materialId: number;
  materialCode: string;
  materialName: string;
  routingVersion: string;
  isCurrent: number;
  totalSteps: number;
  totalHours: number;
  effectiveDate: string;
  expiryDate: string;
  status: number;
  remark: string;
}

export interface RoutingForm {
  routingId?: number;
  routingCode: string;
  routingName: string;
  routingDesc?: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  routingVersion?: string;
  isCurrent?: number;
  totalSteps?: number;
  totalHours?: number;
  effectiveDate?: string;
  expiryDate?: string;
  status?: number;
  remark?: string;
  /** 工序步骤列表（随主表一并保存） */
  steps?: RoutingStepForm[];
}

export interface RoutingStepVO {
  stepId: number;
  routingId: number;
  sequenceNum: number;
  processId: number;
  processCode: string;
  processName: string;
  stepName: string;
  workstationId: number;
  workstationCode: string;
  workstationName: string;
  standardHours: number;
  setupHours: number;
  laborCount: number;
  machineCount: number;
  isKeyProcess: number;
  isOutsource: number;
  qualityRequired: number;
  waitTime: number;
  transferTime: number;
  description: string;
  remark: string;
}

export interface RoutingStepForm {
  stepId?: number;
  routingId: number;
  sequenceNum: number;
  processId: number;
  processCode?: string;
  processName?: string;
  stepName?: string;
  workstationId?: number;
  workstationCode?: string;
  workstationName?: string;
  standardHours?: number;
  setupHours?: number;
  laborCount?: number;
  machineCount?: number;
  isKeyProcess?: number;
  isOutsource?: number;
  qualityRequired?: number;
  waitTime?: number;
  transferTime?: number;
  description?: string;
  remark?: string;
}
