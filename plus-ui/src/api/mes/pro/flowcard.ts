import type { ProductOutputContext } from './completion';
export interface ProFlowCardVO {
  cardId: number;
  cardNo: string;
  mesWoId: number;
  woNo: string;
  materialId: number;
  materialCode: string;
  materialName: string;
  plannedQty: number;
  completedQty: number;
  scrapQty: number;
  currentStepId: number;
  currentStepSeq: number;
  cardStatus: string;
  barcode: string;
  remark: string;
  steps?: ProFlowCardStepVO[];
}

export interface ProFlowCardStepVO {
  /** 既有详情可选的真实产出来源读模型；未提供时不能猜测allocation。 */
  productOutputContext?: ProductOutputContext;
  stepRecordId: number;
  cardId: number;
  cardNo: string;
  mesWoId: number;
  woStepId: number;
  sequenceNum: number;
  processId: number;
  processCode: string;
  processName: string;
  workcenterId: number;
  workcenterCode: string;
  isKeyProcess: number;
  stepStatus: string;
  operatorId: number;
  operatorName: string;
  plannedQty: number;
  goodQty: number;
  defectQty: number;
  scrapQty: number;
  actualStartTime: string;
  actualEndTime: string;
  remark: string;
}

export interface ProFlowCardQuery extends PageQuery {
  cardNo?: string;
  mesWoId?: number;
  cardStatus?: string;
}
