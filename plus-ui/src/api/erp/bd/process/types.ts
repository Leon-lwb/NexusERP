export interface ProcessQuery extends PageQuery {
  processCode?: string;
  processName?: string;
  processCategory?: string;
  workstationId?: number;
  isKeyProcess?: number;
  isOutsource?: number;
  status?: number;
}

export interface ProcessVO {
  processId: number;
  processCode: string;
  processName: string;
  processDesc: string;
  processCategory: string;
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
  status: number;
  remark: string;
  createTime: string;
}

export interface ProcessForm {
  processId?: number;
  processCode: string;
  processName: string;
  processDesc?: string;
  processCategory?: string;
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
  status?: number;
  remark?: string;
}
