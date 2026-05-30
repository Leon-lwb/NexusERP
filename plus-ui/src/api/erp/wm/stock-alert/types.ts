export interface WmStockAlertVO {
  alertId: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  baseUomName: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  currentQty: number;
  safetyStock: number;
  minStock: number;
  maxStock: number;
  alertType: string;
  alertLevel: string;
  handledFlag: number;
  handledTime: string;
  handledBy: number;
  handledRemark: string;
  remark: string;
  createTime: string;
}

export interface WmStockAlertQuery extends PageQuery {
  materialCode?: string;
  materialName?: string;
  alertType?: string;
  alertLevel?: string;
  handledFlag?: number;
}

export interface WmStockAlertForm {
  alertId?: number;
  remark?: string;
}
