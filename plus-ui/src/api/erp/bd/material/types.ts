export interface MaterialQuery extends PageQuery {
  materialCode?: string;
  materialName?: string;
  categoryId?: number;
  materialType?: number;
  status?: number;
  /** 仓库ID：仅查询在该仓库有库存记录的物料 */
  warehouseId?: number;
  /** 库区ID：仅查询在该库区有库存记录的物料 */
  areaId?: number;
  /** 库位ID：仅查询在该库位有库存记录的物料 */
  locationId?: number;
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
  sourceType: string;
  isPhantom: number;
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
  sourceType?: string;
  isPhantom?: number;
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
