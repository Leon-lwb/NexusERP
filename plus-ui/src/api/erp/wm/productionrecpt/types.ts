import type { TraceDocumentFields } from '@/api/erp/wm/identity/types';

export interface ProductionRecptQuery extends PageQuery {
  recptCode?: string;
  woId?: string | number;
  woNo?: string;
  status?: string;
}

export interface ProductionRecptVO extends TraceDocumentFields {
  recptId: string | number;
  recptCode: string;
  woId: string | number;
  woNo: string;
  materialId: string | number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  quantity: string | number;
  warehouseId: string | number;
  warehouseName: string;
  locationId: string | number;
  locationName: string;
  status: string;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface ProductionRecptForm extends TraceDocumentFields {
  recptId?: string | number;
  recptCode?: string;
  woId?: string | number;
  woNo?: string;
  materialId?: string | number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  quantity?: string | number;
  warehouseId?: string | number;
  warehouseCode?: string;
  warehouseName?: string;
  locationId?: string | number;
  locationCode?: string;
  locationName?: string;
  status?: string;
  remark?: string;
}
