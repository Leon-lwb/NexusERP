export interface MaterialQuery extends PageQuery {
  materialCode?: string;
  materialName?: string;
  categoryId?: number;
  materialType?: number;
  status?: number;
}

export interface MaterialVO {
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  materialDesc: string;
  categoryId: number;
  categoryName: string;
  baseUomId: number;
  baseUomName: string;
  purchaseUomId: number;
  inventoryUomId: number;
  saleUomId: number;
  materialType: number;
  sourceType: number;
  isBatchManage: number;
  isSerialManage: number;
  safetyStock: number;
  minStock: number;
  maxStock: number;
  standardCost: number;
  referencePrice: number;
  status: number;
  qualityStatus: string;
  shelfLife: number;
  weight: number;
  volume: number;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface MaterialForm {
  materialId?: number;
  materialCode: string;
  materialName: string;
  materialSpec?: string;
  materialDesc?: string;
  categoryId: number;
  baseUomId?: number;
  purchaseUomId?: number;
  inventoryUomId?: number;
  saleUomId?: number;
  materialType: number;
  sourceType?: number;
  isBatchManage?: number;
  isSerialManage?: number;
  safetyStock?: number;
  minStock?: number;
  maxStock?: number;
  standardCost?: number;
  referencePrice?: number;
  status?: number;
  qualityStatus?: string;
  shelfLife?: number;
  weight?: number;
  volume?: number;
  remark?: string;
}
