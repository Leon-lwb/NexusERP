/** 后端十进制数在线协议中可能序列化为字符串，比较前须严格归一。 */
export type ProductionIssueDecimal = string | number;

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
  totalQty?: ProductionIssueDecimal;
  /** 参考总金额；任一行未计价时为空，不代表实际库存成本。 */
  totalAmount?: ProductionIssueDecimal | null;
  /** 读端参考金额完整已知状态，前端不能用旧单头零值推断。 */
  referenceAmountKnown?: boolean;
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
  quantity?: ProductionIssueDecimal;
  /** BOM标准用量（单位产品用量） */
  bomQty?: ProductionIssueDecimal;
  /** 损耗率(%) */
  lossRate?: ProductionIssueDecimal;
  /** 应领数量（BOM定额×工单数量） */
  requiredQty?: ProductionIssueDecimal;
  /** 已领数量（累计已发） */
  issuedQty?: ProductionIssueDecimal | null;
  /** 本次领料数量 */
  currentQty?: ProductionIssueDecimal | null;
  /** 欠领数量（应领-已领） */
  shortQty?: ProductionIssueDecimal | null;
  /** 物料需求明细ID（追溯工单BOM需求） */
  requirementDetailId?: number;
  /** 行状态(PENDING/PARTIAL/ISSUED) */
  lineStatus?: string;
  backflushFlag?: number;
  /** 参考单价；未知为空，已知零价保留零。 */
  unitPrice?: ProductionIssueDecimal | null;
  /** 仅主动清价时为真；省略不清既有参考价格，不持久化。 */
  clearReferencePrice?: boolean;
  /** 参考金额；未知为空，不以库存成本补价。 */
  amount?: ProductionIssueDecimal | null;
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
  totalQty: ProductionIssueDecimal;
  /** 参考总金额；未计价为空，不能依据旧单头零值推断已计价。 */
  totalAmount: ProductionIssueDecimal | null;
  /** 有效明细完整计价才为真，缺失状态不得默认已知。 */
  referenceAmountKnown: boolean;
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
  /** 真实账面库存量，不作为可用量。 */
  stockQuantity: ProductionIssueDecimal | null;
  /** 真实预留量，未知不能默认为零。 */
  reservedQuantity: ProductionIssueDecimal | null;
  /** 可用量为账面量减预留量并截零，未知为空。 */
  availableQuantity: ProductionIssueDecimal | null;
  unitCost?: ProductionIssueDecimal;
  recptDate?: string;
  expireDate?: string;
}
