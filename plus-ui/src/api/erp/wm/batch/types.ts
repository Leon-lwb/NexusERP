export interface WmBatchVO {
  batchId: string;
  batchCode: string;
  materialId: string;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  vendorId: string;
  vendorCode: string;
  vendorName: string;
  warehouseId: string;
  warehouseCode: string;
  warehouseName: string;
  productionDate: string;
  expiryDate: string;
  quantity: string;
  receivedQty: string;
  issuedQty: string;
  status: string;
  sourceDocType: string;
  sourceDocId: string;
  sourceDocNo: string;
  ruleId: string;
  remark: string;
  createTime: string;
  updateTime: string;
}

export interface WmBatchQuery extends PageQuery {
  batchCode?: string;
  materialId?: string;
  materialCode?: string;
  materialName?: string;
  vendorCode?: string;
  warehouseId?: string;
  status?: string;
}

export interface WmBatchForm {
  batchId?: string;
  batchCode?: string;
  materialId?: string;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  vendorId?: string;
  vendorCode?: string;
  vendorName?: string;
  warehouseId?: string;
  warehouseCode?: string;
  warehouseName?: string;
  productionDate?: string;
  expiryDate?: string;
  quantity?: string;
  receivedQty?: string;
  issuedQty?: string;
  status?: string;
  sourceDocType?: string;
  sourceDocId?: string;
  sourceDocNo?: string;
  ruleId?: string;
  remark?: string;
}
