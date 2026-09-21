/** 生产统计报表查询（完工率 / 生产效率） */
export interface ProductionReportQuery {
  months?: number;
}

/**
 * 生产报工查询条件
 * <p>字段与后端 {@code PrdWorkReportServiceImpl.buildQueryWrapper} 实际支持的筛选条件一一对应
 * （reportNo/reportType/reportStatus/woNo/woName/materialCode/materialName/qualityStatus/
 * workerId/workerName/reportChannel/sourceType/sourceNo/barcode），
 * 不支持的字段一律不放进查询表单，避免"填了没效果"。</p>
 */
export interface WorkReportQuery extends PageQuery {
  reportNo?: string;
  reportType?: string;
  reportStatus?: string;
  woNo?: string;
  woName?: string;
  materialCode?: string;
  materialName?: string;
  qualityStatus?: string;
  workerId?: number;
  workerName?: string;
  reportChannel?: string;
  sourceType?: string;
  sourceNo?: string;
  barcode?: string;
}

/** 生产报工视图对象（对应后端 PrdWorkReportVo 业务字段） */
export interface WorkReportVO {
  reportId?: number;
  reportNo?: string;
  reportType?: string;
  reportStatus?: string;
  woId?: number;
  woNo?: string;
  woName?: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  specification?: string;
  unitId?: number;
  unitCode?: string;
  unitName?: string;
  routeId?: number;
  routeCode?: string;
  routeName?: string;
  processId?: number;
  processCode?: string;
  processName?: string;
  operationStep?: number;
  operationSeq?: number;
  taskId?: number;
  taskNo?: string;
  workstationId?: number;
  workstationCode?: string;
  workstationName?: string;
  workshopId?: number;
  workshopName?: string;
  equipmentId?: number;
  equipmentCode?: string;
  equipmentName?: string;
  lotNumber?: string;
  batchNo?: string;
  batchId?: number;
  batchCode?: string;
  expireDate?: string;
  shiftId?: number;
  shiftName?: string;
  plannedQty?: number;
  reportedQty?: number;
  goodQty?: number;
  scrapQty?: number;
  defectQty?: number;
  laborScrapQty?: number;
  materialScrapQty?: number;
  otherScrapQty?: number;
  uncheckQty?: number;
  cumulativeQty?: number;
  remainingQty?: number;
  reportDate?: string;
  startTime?: string;
  endTime?: string;
  laborHours?: number;
  machineHours?: number;
  setupHours?: number;
  waitHours?: number;
  pauseHours?: number;
  workerId?: number;
  workerCode?: string;
  workerName?: string;
  teamId?: number;
  teamCode?: string;
  teamName?: string;
  helperIds?: string;
  helperNames?: string;
  qualityStatus?: string;
  qualityCheckId?: number;
  qualityCheckNo?: string;
  inspectorId?: number;
  inspectorName?: string;
  inspectTime?: string;
  reportChannel?: string;
  sourceType?: string;
  sourceId?: number;
  sourceNo?: string;
  barcode?: string;
  submitBy?: string;
  submitTime?: string;
  auditBy?: string;
  auditTime?: string;
  auditRemark?: string;
  remark?: string;
}

/** 生产报工新增/修改表单（与 BO 字段名一致） */
export interface WorkReportForm extends WorkReportVO {}

/** 生产报工状态常量（后端 PrdWorkReportServiceImpl 判定 APPROVED 时回写工单累计产量） */
export const REPORT_STATUS = {
  DRAFT: 'DRAFT',
  SUBMITTED: 'SUBMITTED',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED'
} as const;
