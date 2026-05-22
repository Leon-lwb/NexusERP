import { PageQuery } from '@/types/common';

export interface StockTakingPlanQuery extends PageQuery {
  planCode?: string;
  planName?: string;
  warehouseId?: number;
  status?: string;
}

export interface StockTakingLineVO {
  lineId?: number;
  planId?: number;
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
  stockId?: number;
  bookQuantity?: number;
  actualQuantity?: number;
  differenceQuantity?: number;
  status?: string;
  remark?: string;
}

export interface StockTakingPlanVO {
  planId?: number;
  planCode?: string;
  planName?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  areaId?: number;
  areaCode?: string;
  areaName?: string;
  plannedDate?: string;
  status?: string;
  remark?: string;
  wmStockTakingLineList?: StockTakingLineVO[];
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
}

export interface StockTakingPlanForm {
  planId?: number;
  planCode: string;
  planName?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  areaId?: number;
  areaCode?: string;
  areaName?: string;
  plannedDate?: string;
  status?: string;
  remark?: string;
  wmStockTakingLineList?: StockTakingLineVO[];
}
