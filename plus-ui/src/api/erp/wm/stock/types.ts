export interface StockQuery extends PageQuery {
  materialCode?: string;
  materialName?: string;
  batchCode?: string;
  warehouseName?: string;
  areaName?: string;
  locationId?: number;
  locationName?: string;
  vendorName?: string;
  woNo?: string;
  recptDate?: string;
  expireDate?: string;
  frozenFlag?: number;
}

export interface StockVO {
  stockId: number;
  categoryId: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  baseUomId: string;
  baseUomName: string;
  batchId: number;
  batchCode: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  areaId: number;
  areaCode: string;
  areaName: string;
  locationId: number;
  locationCode: string;
  locationName: string;
  vendorId: number;
  vendorCode: string;
  vendorName: string;
  vendorNick: string;
  stockQuantity: number;
  woId: number;
  woNo: string;
  productionDate: string;
  recptDate: string;
  expireDate: string;
  frozenFlag: number;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface StockForm {
  stockId?: number;
  categoryId?: number;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  baseUomId?: string;
  baseUomName?: string;
  batchId?: number;
  batchCode?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  areaId?: number;
  areaCode?: string;
  areaName?: string;
  locationId?: number;
  locationCode?: string;
  locationName?: string;
  vendorId?: number;
  vendorCode?: string;
  vendorName?: string;
  vendorNick?: string;
  stockQuantity?: number;
  woId?: number;
  woNo?: string;
  productionDate?: string;
  recptDate?: string;
  expireDate?: string;
  frozenFlag?: number;
}
