export interface ValuationQuery extends PageQuery {
  materialCode?: string;
  materialName?: string;
  warehouseId?: number;
}

export interface ValuationVO {
  valuationId: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  specification: string;
  unitName: string;
  quantity: number;
  unitCost: number;
  totalCost: number;
  warehouseId: number;
  warehouseName: string;
  locationName: string;
  createTime: string;
}
