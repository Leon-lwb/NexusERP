export interface ProductionIssueQuery {
  pageNum: number;
  pageSize: number;
  issueCode?: string;
  woId?: number;
  woNo?: string;
  warehouseId?: number;
  status?: string;
}

export interface ProductionIssueForm {
  issueId?: number;
  issueCode?: string;
  woId?: number;
  woNo?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  issueDate?: string;
  issueType?: string;
  issuerId?: number;
  issuerName?: string;
  totalQty?: number;
  totalAmount?: number;
  status?: string;
  remark?: string;
  wmProductionIssueLineList?: ProductionIssueLineForm[];
}

export interface ProductionIssueLineForm {
  lineId?: number;
  issueId?: number;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  baseUomId?: string;
  baseUomName?: string;
  batchId?: number;
  batchCode?: string;
  locationId?: number;
  locationCode?: string;
  locationName?: string;
  quantity?: number;
  /** BOM标准用量（单位产品用量） */
  bomQty?: number;
  /** 损耗率(%) */
  lossRate?: number;
  /** 应领数量（BOM定额×工单数量） */
  requiredQty?: number;
  /** 已领数量（累计已发） */
  issuedQty?: number;
  /** 本次领料数量 */
  currentQty?: number | null;
  /** 欠领数量（应领-已领） */
  shortQty?: number;
  /** 物料需求明细ID（追溯工单BOM需求） */
  requirementDetailId?: number;
  /** 行状态(PENDING/PARTIAL/ISSUED) */
  lineStatus?: string;
  backflushFlag?: number;
  unitPrice?: number;
  amount?: number;
  remark?: string;
}

export interface ProductionIssueVO {
  issueId: number;
  issueCode: string;
  woId: number;
  woNo: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  issueDate: string;
  issueType: string;
  issuerId: number;
  issuerName: string;
  totalQty: number;
  totalAmount: number;
  status: string;
  remark: string;
  wmProductionIssueLineList: ProductionIssueLineForm[];
}

/** 可用库存（ATP）明细，供批次/库位选择与可用量校验 */
export interface AvailableStockVO {
  stockId: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  batchId?: number;
  batchCode?: string;
  warehouseId?: number;
  warehouseName?: string;
  areaId?: number;
  areaCode?: string;
  locationId?: number;
  locationCode?: string;
  locationName?: string;
  stockQuantity: number;
  unitCost?: number;
  recptDate?: string;
  expireDate?: string;
}
