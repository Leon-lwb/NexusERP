/** 正式SN身份与版本按服务端字符串保留，库存使用资格由后端原事实核验。 */
export interface WmSnVO {
  identityId?: string;
  canonicalSnCode?: string;
  identityVersion?: string;
  snId: string;
  snCode: string;
  materialId: string;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  batchId: string;
  batchCode: string;
  warehouseId: string;
  warehouseCode: string;
  warehouseName: string;
  locationId: string;
  locationCode: string;
  stockId: string;
  status: string;
  sourceDocType: string;
  sourceDocId: string | null;
  sourceDocNo: string;
  ruleId: string;
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
  warehouseId?: string;
}

export interface WmSnForm {
  snId?: string;
  snCode?: string;
  materialId?: string;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  batchId?: string;
  batchCode?: string;
  warehouseId?: string;
  warehouseCode?: string;
  warehouseName?: string;
  locationId?: string;
  locationCode?: string;
  stockId?: string;
  status?: string;
  sourceDocType?: string;
  sourceDocId?: string;
  sourceDocNo?: string;
  ruleId?: string;
  productionDate?: string;
  expiryDate?: string;
  remark?: string;
}
