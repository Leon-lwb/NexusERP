import type { TraceDocumentFields } from '@/api/erp/wm/identity/types';

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

export interface DetailVO extends TraceDocumentFields {
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

export interface DetailForm extends TraceDocumentFields {
  detailId?: number | string;
  lineId?: number | string;
  receiveId?: number | string;
  materialId?: number | string;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  baseUomId?: string;
  baseUomName?: string;
  quantity?: number | string;
  batchId?: number | string;
  batchCode?: string;
  warehouseId?: number | string;
  warehouseCode?: string;
  warehouseName?: string;
  areaId?: number | string;
  areaCode?: string;
  areaName?: string;
  locationId?: number | string;
  locationCode?: string;
  locationName?: string;
  remark?: string;
  attr1?: string;
  attr2?: string;
  attr3?: string;
  attr4?: string;
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
}
