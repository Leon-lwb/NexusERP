
export interface WmSnVO {
  snId: number;
  snCode: string;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  batchId: number;
  batchCode: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  locationId: number;
  locationCode: string;
  stockId: number;
  status: string;
  sourceDocType: string;
  sourceDocId: number;
  sourceDocNo: string;
  ruleId: number;
  productionDate: string;
  expiryDate: string;
  remark: string;
  createTime: string;
  updateTime: string;
}

export interface WmSnQuery extends PageQuery {
  snCode?: string;
  materialCode?: string;
  materialName?: string;
  status?: string;
  batchCode?: string;
  warehouseId?: number;
}

export interface WmSnForm {
  snId?: number;
  snCode?: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  batchId?: number;
  batchCode?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  locationId?: number;
  locationCode?: string;
  stockId?: number;
  status?: string;
  sourceDocType?: string;
  sourceDocId?: number;
  sourceDocNo?: string;
  ruleId?: number;
  productionDate?: string;
  expiryDate?: string;
  remark?: string;
}
