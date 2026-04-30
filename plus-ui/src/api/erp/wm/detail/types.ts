export interface DetailQuery extends PageQuery {
  lineId?: number;
  receiveId?: number;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  quantity?: number;
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
}

export interface DetailVO {
  detailId: number;
  lineId: number;
  receiveId: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  baseUomId: string;
  baseUomName: string;
  quantity: number;
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
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface DetailForm {
  detailId?: number;
  lineId?: number;
  receiveId?: number;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  baseUomId?: string;
  baseUomName?: string;
  quantity?: number;
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
  remark?: string;
}
