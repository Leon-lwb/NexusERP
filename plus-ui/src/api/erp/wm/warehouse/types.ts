export interface WarehouseQuery extends PageQuery {
  warehouseCode?: string;
  warehouseName?: string;
  location?: string;
  area?: number;
  manager?: string;
  status?: number;
}

export interface WarehouseVO {
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  location: string;
  area: number;
  manager: string;
  managerId: number;
  remark: string;
  status: number;
  attr1: string;
  attr2: string;
  attr3: number;
  attr4: number;
  provinceAreaId: number;
  cityAreaId: number;
  districtAreaId: number;
  streetId: number;
  addressLat: string;
  addressLng: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface WarehouseForm {
  warehouseId?: number;
  warehouseCode: string;
  warehouseName: string;
  location?: string;
  area?: number;
  manager?: string;
  managerId?: number;
  remark?: string;
  status?: number;
  attr1?: string;
  attr2?: string;
  attr3?: number;
  attr4?: number;
  provinceAreaId?: number;
  cityAreaId?: number;
  districtAreaId?: number;
  streetId?: number;
  addressLat?: string;
  addressLng?: string;
}
