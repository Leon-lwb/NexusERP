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
  issueCode: string;
  woId?: number;
  woNo?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  issueDate?: string;
  issueType?: string;
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
  status: string;
  remark: string;
  wmProductionIssueLineList: ProductionIssueLineForm[];
}
