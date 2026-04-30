export interface WorkstationQuery extends PageQuery {
  workstationCode?: string;
  workstationName?: string;
  workshopId?: number;
  status?: number;
}

export interface WorkstationVO {
  workstationId: number;
  workstationCode: string;
  workstationName: string;
  workshopId: number;
  workshopCode: string;
  workshopName: string;
  workstationDesc: string;
  equipmentId: number;
  operatorCount: number;
  standardCapacity: number;
  status: number;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface WorkstationForm {
  workstationId?: number;
  workstationCode: string;
  workstationName: string;
  workshopId: number;
  workshopCode?: string;
  workshopName?: string;
  workstationDesc?: string;
  equipmentId?: number;
  operatorCount?: number;
  standardCapacity?: number;
  status?: number;
  remark?: string;
}
