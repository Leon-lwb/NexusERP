/** 预留明细（RESERVE/UNRESERVE 流水） */
export interface ReservationTxVO {
  txId: number;
  txType: string;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  baseUomName: string;
  batchCode: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  areaCode: string;
  locationCode: string;
  sourceDocType: string;
  sourceDocCode: string;
  sourceDocId: number;
  sourceDocLineId: number;
  txFlag: number;
  txQuantity: number;
  beforeQuantity: number;
  afterQuantity: number;
  txDate: string;
  erpDate: string;
  recptDate: string;
  createBy: number;
  createTime: string;
}

/** 预留汇总（在库/已预留/可用） */
export interface ReservationSummaryVO {
  stockId: number;
  categoryId: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  baseUomName: string;
  batchCode: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  areaCode: string;
  areaName: string;
  locationCode: string;
  locationName: string;
  vendorCode: string;
  vendorName: string;
  stockQuantity: number;
  reservedQuantity: number;
  availableQuantity: number;
  woNo: string;
  createTime: string;
}

/** 查询条件 */
export interface ReservationQuery extends PageQuery {
  materialCode?: string;
  materialName?: string;
  warehouseCode?: string;
  sourceDocType?: string;
  sourceDocCode?: string;
}

/** 手动预留/释放表单 */
export interface ReserveForm {
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  warehouseId?: number;
  warehouseName?: string;
  reserveQty?: number;
  sourceDocType?: string;
  sourceDocId?: number;
  sourceDocLineId?: number;
  /** 销售订单编号（展示用） */
  soNo?: string;
  /** 生产工单编号（展示用） */
  woNo?: string;
}
