export interface ProWorkOrderVO {
  mesWoId: number;
  erpWoId: number;
  woNo: string;
  materialId: number;
  materialCode: string;
  materialName: string;
  plannedQty: number;
  completedQty: number;
  scrapQty: number;
  unitId: number;
  unitName: string;
  planStartDate: string;
  planEndDate: string;
  actualStartDate: string;
  actualEndDate: string;
  workshopId: number;
  workshopName: string;
  bomId: number;
  routingId: number;
  woStatus: string;
  priority: number;
  remark: string;
  selectFlowId: number;
  steps?: ProWorkOrderStepVO[];
}

export interface ProWorkOrderStepVO {
  stepId: number;
  mesWoId: number;
  sequenceNum: number;
  processId: number;
  processCode: string;
  processName: string;
  workcenterId: number;
  workcenterCode: string;
  plannedQty: number;
  completedQty: number;
  scrapQty: number;
  stepStatus: string;
  isKeyProcess: number;
  startTime: string;
  endTime: string;
  remark: string;
}

export interface ProWorkOrderForm {
  mesWoId?: number;
  erpWoId?: number;
  woNo?: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  plannedQty?: number;
  workshopId?: number;
  workshopName?: string;
  bomId?: number;
  routingId?: number;
  woStatus?: string;
  priority?: number;
  remark?: string;
  selectFlowId?: number;
}

export interface ProWorkOrderQuery extends PageQuery {
  woNo?: string;
  erpWoId?: number;
  materialId?: number;
  materialName?: string;
  workshopId?: number;
  woStatus?: string;
}
