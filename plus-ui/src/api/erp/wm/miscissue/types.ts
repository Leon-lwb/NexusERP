export interface MiscIssueQuery extends PageQuery {
  issueCode?: string;
  warehouseId?: number;
  status?: string;
}

export interface MiscIssueLineVO {
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
  unitPrice?: number;
  amount?: number;
  remark?: string;
}

export interface MiscIssueVO {
  issueId?: number;
  issueCode?: string;
  issueType?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  issueDate?: string;
  status?: string;
  remark?: string;
  wmMiscIssueLineList?: MiscIssueLineVO[];
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
}

export interface MiscIssueForm {
  issueId?: number;
  issueCode: string;
  issueType?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  issueDate?: string;
  status?: string;
  remark?: string;
  wmMiscIssueLineList?: MiscIssueLineVO[];
}
