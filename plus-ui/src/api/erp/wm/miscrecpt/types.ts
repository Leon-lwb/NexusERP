import type { TraceDocumentFields } from '@/api/erp/wm/identity/types';

export interface MiscRecptQuery extends PageQuery {
  recptCode?: string;
  warehouseId?: number;
  status?: string;
}

export interface MiscRecptLineVO extends TraceDocumentFields {
  lineId?: number;
  recptId?: number;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  baseUomId?: string;
  baseUomName?: string;
  batchId?: number;
  batchCode?: string;
  locationId?: number;
  locationCode?: string;
  locationName?: string;
  quantity?: number;
  unitPrice?: number;
  amount?: number;
  remark?: string;
}

export interface MiscRecptVO extends TraceDocumentFields {
  recptId?: number;
  recptCode?: string;
  recptType?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  areaId?: number;
  areaCode?: string;
  areaName?: string;
  recptDate?: string;
  status?: string;
  remark?: string;
  wmMiscRecptLineList?: MiscRecptLineVO[];
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
}

export interface MiscRecptForm extends TraceDocumentFields {
  recptId?: number;
  recptCode: string;
  recptType?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  areaId?: number;
  areaCode?: string;
  areaName?: string;
  recptDate?: string;
  status?: string;
  remark?: string;
  wmMiscRecptLineList?: MiscRecptLineVO[];
}
