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
  /** MBOM快照明细(详情查询时填充) */
  bomLines?: ProWorkOrderBomLineVO[];
  /** 工艺路线编码(富集) */
  routingCode?: string;
  /** 工艺路线名称(富集) */
  routingName?: string;
  /** BOM版本号(富集) */
  bomVersion?: string;
  /** 关联设备ID(P1-6) */
  equipmentId?: number;
  /** 关联设备编码(富集) */
  equipmentCode?: string;
  /** 关联设备名称(富集) */
  equipmentName?: string;
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
  equipmentId?: number;
  woStatus?: string;
  priority?: number;
  planStartDate?: string;
  planEndDate?: string;
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

/** 工单BOM快照明细 */
export interface ProWorkOrderBomLineVO {
  woBomLineId: number;
  mesWoId: number;
  bomId: number;
  bomDetailId: number;
  mbomLineId: number;
  childMaterialId: number;
  childMaterialCode: string;
  childMaterialName: string;
  quantityPerParent: number;
  lossRate: number;
  totalQuantity: number;
  sequenceNum: number;
  processCode: string;
  processName: string;
  workstationCode: string;
  workstationName: string;
  operationContent: string;
  backflushFlag: number;
  sortOrder: number;
  remark: string;
}

/** MBOM VO */
export interface MesMbomVO {
  mbomId: number;
  bomId: number;
  routingId: number;
  mbomVersion: string;
  mbomStatus: string;
  remark: string;
  bomVersion: string;
  parentMaterialCode: string;
  parentMaterialName: string;
  routingCode: string;
  routingName: string;
  lines?: MesMbomLineVO[];
  createTime: string;
  updateTime: string;
}

/** MBOM明细行 VO */
export interface MesMbomLineVO {
  mbomLineId: number;
  mbomId: number;
  bomDetailId: number;
  childMaterialId: number;
  childMaterialCode: string;
  childMaterialName: string;
  childMaterialSpec: string;
  quantityPerParent: number;
  lossRate: number;
  routingStepId: number;
  routingStepSequence: number;
  processId: number;
  processCode: string;
  processName: string;
  workstationId: number;
  workstationCode: string;
  workstationName: string;
  operationContent: string;
  backflushFlag: number;
  sortOrder: number;
  remark: string;
}

/** MBOM Form */
export interface MesMbomForm {
  mbomId?: number;
  bomId: number;
  routingId?: number;
  mbomVersion?: string;
  mbomStatus?: string;
  remark?: string;
  lines?: MesMbomLineForm[];
}

/** MBOM明细行 Form */
export interface MesMbomLineForm {
  mbomLineId?: number;
  bomDetailId?: number;
  childMaterialId: number;
  quantityPerParent: number;
  lossRate?: number;
  routingStepId?: number;
  processId?: number;
  workstationId?: number;
  operationContent?: string;
  backflushFlag?: number;
  sortOrder?: number;
  remark?: string;
}

/** MBOM Query */
export interface MesMbomQuery extends PageQuery {
  bomId?: number;
  routingId?: number;
  mbomStatus?: string;
}
