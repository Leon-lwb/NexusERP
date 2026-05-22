import { PageQuery } from '@/types/common';

export interface TransferQuery extends PageQuery {
  transferNo?: string;
  fromWarehouseId?: number;
  toWarehouseId?: number;
  status?: string;
}

export interface TransferLineVO {
  lineId?: number;
  transferId?: number;
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
  toLocationCode?: string;
  toLocationName?: string;
  quantity?: number;
  remark?: string;
}

export interface TransferVO {
  transferId?: number;
  transferNo?: string;
  fromWarehouseId?: number;
  fromWarehouseCode?: string;
  fromWarehouseName?: string;
  fromAreaId?: number;
  fromAreaCode?: string;
  fromAreaName?: string;
  toWarehouseId?: number;
  toWarehouseCode?: string;
  toWarehouseName?: string;
  toAreaId?: number;
  toAreaCode?: string;
  toAreaName?: string;
  transferType?: string;
  totalQuantity?: number;
  status?: string;
  operatorId?: number;
  operatorName?: string;
  transferDate?: string;
  executeDate?: string;
  selectFlowId?: string;
  remark?: string;
  wmTransferLineList?: TransferLineVO[];
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
}

export interface TransferForm {
  transferId?: number;
  transferNo: string;
  fromWarehouseId?: number;
  fromWarehouseCode?: string;
  fromWarehouseName?: string;
  fromAreaId?: number;
  fromAreaCode?: string;
  fromAreaName?: string;
  toWarehouseId?: number;
  toWarehouseCode?: string;
  toWarehouseName?: string;
  toAreaId?: number;
  toAreaCode?: string;
  toAreaName?: string;
  transferType?: string;
  totalQuantity?: number;
  status?: string;
  operatorId?: number;
  operatorName?: string;
  transferDate?: string;
  executeDate?: string;
  selectFlowId?: string;
  remark?: string;
  wmTransferLineList?: TransferLineVO[];
}
