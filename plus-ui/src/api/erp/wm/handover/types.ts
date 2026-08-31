export interface HandoverQuery extends PageQuery {
  handoverNo?: string;
  woNo?: string;
  status?: string;
}

export interface HandoverLineVO {
  lineId?: number;
  handoverId?: number;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  baseUomId?: string;
  baseUomName?: string;
  batchId?: number;
  batchCode?: string;
  fromLocationId?: number;
  fromLocationCode?: string;
  fromLocationName?: string;
  toLocationId?: number;
  /** 线边库位编码 (必填, 过账按其归属仓库入库) */
  toLocationCode?: string;
  toLocationName?: string;
  /** 计划交接数量 */
  quantity?: number;
  /** PDA 已核销数量 */
  actualQty?: number;
  remark?: string;
}

export interface HandoverVO {
  handoverId?: number;
  handoverNo?: string;
  woId?: number;
  woNo?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  /** DRAFT/SIGNED */
  status?: string;
  handoverTime?: string;
  signBy?: number;
  signName?: string;
  signTime?: string;
  remark?: string;
  lines?: HandoverLineVO[];
}

export interface HandoverForm {
  handoverId?: number;
  woId?: number;
  woNo?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  remark?: string;
  lines?: HandoverLineVO[];
}
