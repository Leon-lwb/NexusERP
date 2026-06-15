export interface InventorySummaryQuery {
  warehouseId?: number;
  categoryCode?: string;
}

export interface InventoryAgingQuery {
  warehouseId?: number;
  daysThreshold?: number;
}

export interface InventoryTurnoverQuery {
  categoryCode?: string;
  months?: number;
}
