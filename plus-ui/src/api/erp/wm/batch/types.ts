export interface WmBatchVO {
  batchId: number;
  batchCode: string;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  vendorId: number;
  vendorCode: string;
  vendorName: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  productionDate: string;
  expiryDate: string;
  quantity: number;
  receivedQty: number;
  issuedQty: number;
  status: string;
  sourceDocType: string;
  sourceDocId: number;
  sourceDocNo: string;
  ruleId: number;
  remark: string;
  createTime: string;
  updateTime: string;
}

export interface WmBatchQuery extends PageQuery {
  batchCode?: string;
  materialId?: number | string;
  materialCode?: string;
  materialName?: string;
  vendorCode?: string;
  warehouseId?: number;
  status?: string;
}

export interface WmBatchForm {
  batchId?: number;
  batchCode?: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  vendorId?: number;
  vendorCode?: string;
  vendorName?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  productionDate?: string;
  expiryDate?: string;
  quantity?: number;
  receivedQty?: number;
  issuedQty?: number;
  status?: string;
  sourceDocType?: string;
  sourceDocId?: number;
  sourceDocNo?: string;
  ruleId?: number;
  remark?: string;
}
