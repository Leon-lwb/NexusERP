export interface StockTakingPlanQuery extends PageQuery {
  planCode?: string;
  planName?: string;
  warehouseId?: number;
  status?: string;
  /** 盘点方式（OPEN 明盘 / BLIND 盲盘） */
  countMode?: string;
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
  /** 盘点方式（后端 WmStockTakingPlanBo/Vo/实体 countMode；PDA 端已按 OPEN/BLIND 分支） */
  countMode?: string;
  selectFlowId?: number;
  actProcessId?: string;
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
  /** 盘点方式（OPEN 明盘 / BLIND 盲盘） */
  countMode?: string;
  selectFlowId?: number;
  remark?: string;
  wmStockTakingLineList?: StockTakingLineVO[];
}
