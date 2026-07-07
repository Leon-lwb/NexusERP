export interface ProductionRecptQuery extends PageQuery {
  recptCode?: string;
  woId?: number;
  woNo?: string;
  status?: string;
}

export interface ProductionRecptVO {
  recptId: number;
  recptCode: string;
  woId: number;
  woNo: string;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  quantity: number;
  warehouseId: number;
  warehouseName: string;
  locationId: number;
  locationName: string;
  status: string;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface ProductionRecptForm {
  recptId?: number;
  recptCode?: string;
  woId?: number;
  woNo?: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  quantity?: number;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  locationId?: number;
  locationCode?: string;
  locationName?: string;
  status?: string;
  remark?: string;
}
