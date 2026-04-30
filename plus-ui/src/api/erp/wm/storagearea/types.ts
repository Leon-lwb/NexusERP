export interface StorageareaQuery extends PageQuery {
  areaCode?: string;
  areaName?: string;
  warehouseId?: number;
  locationFlag?: number;
  enableFlag?: number;
  frozenFlag?: number;
  layoutType?: string;
}

export interface StorageareaVO {
  areaId: number;
  areaCode: string;
  areaName: string;
  warehouseId: number;
  area: number;
  locationFlag: number;
  enableFlag: number;
  frozenFlag: number;
  remark: string;
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
  modelUrl: string;
  showWalls: number;
  showLabel: number;
  gridVisible: number;
  configJson: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface StorageareaForm {
  areaId?: number;
  areaCode: string;
  areaName: string;
  warehouseId?: number;
  area?: number;
  locationFlag?: number;
  enableFlag?: number;
  frozenFlag?: number;
  remark?: string;
  layoutType?: string;
  posX?: number;
  posY?: number;
  posZ?: number;
  areaWidth?: number;
  areaLength?: number;
  areaHeight?: number;
  rotationX?: number;
  rotationY?: number;
  rotationZ?: number;
  color?: string;
  opacity?: number;
  modelType?: string;
  modelUrl?: string;
  showWalls?: number;
  showLabel?: number;
  gridVisible?: number;
  configJson?: string;
}
