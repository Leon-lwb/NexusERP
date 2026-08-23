export interface MaterialQuery extends PageQuery {
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  categoryId?: number | string;
  /** 分类ID集合（含子孙分类，用于左侧分类树筛选；雪花ID实际为字符串） */
  categoryIds?: string[] | number[];
  materialType?: number | string;
  sourceType?: string;
  status?: number | string;
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
  inspectionRequired?: number;
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
  materialId?: number | string;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  materialDesc?: string;
  categoryId?: number | string;
  baseUomId?: number | string;
  purchaseUomId?: number | string;
  inventoryUomId?: number | string;
  saleUomId?: number | string;
  materialType?: number | string;
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
  inspectionRequired?: number;
  shelfLife?: number;
  weight?: number;
  volume?: number;
  remark?: string;
  attr1?: string;
  attr2?: string;
  attr3?: string;
  attr4?: string;
  createBy?: number | string;
  createTime?: string;
  updateBy?: number | string;
  updateTime?: string;
}
