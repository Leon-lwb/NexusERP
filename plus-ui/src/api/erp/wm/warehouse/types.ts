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

/** 3D 可视化 - 库位视图 */
export interface WarehouseLocation3dVO {
  locationId: number;
  locationCode: string;
  locationName: string;
  areaId: number;
  posX: number;
  posY: number;
  posZ: number;
  width: number;
  height: number;
  locationDepth: number;
  rotationX: number;
  rotationY: number;
  rotationZ: number;
  colorEmpty: string;
  colorOccupied: string;
  colorLocked: string;
  colorWarning: string;
  opacity: number;
  frozenFlag: number;
  enableFlag: number;
  rackId: string;
  layer: number;
  col: number;
  rowNum: number;
  /** 库位状态: EMPTY/OCCUPIED/WARNING/LOCKED */
  status: string;
  stockQuantity: number;
  reservedQuantity: number;
  stockCount: number;
}

/** 3D 可视化 - 库区视图 */
export interface WarehouseArea3dVO {
  areaId: number;
  areaCode: string;
  areaName: string;
  warehouseId: number;
  layoutType: string;
  posX: number;
  posY: number;
  posZ: number;
  areaWidth: number;
  areaLength: number;
  areaHeight: number;
  rotationX: number;
  rotationY: number;
  rotationZ: number;
  color: string;
  opacity: number;
  modelType: string;
  configJson: string;
  locations: WarehouseLocation3dVO[];
}

/** 3D 可视化 - 仓库聚合视图 */
export interface Warehouse3dVO {
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  status: number;
  areas: WarehouseArea3dVO[];
  totalLocations: number;
  occupiedLocations: number;
  emptyLocations: number;
  lockedLocations: number;
  warningLocations: number;
}
