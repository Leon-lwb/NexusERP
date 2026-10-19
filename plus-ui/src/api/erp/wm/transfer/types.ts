import type { TraceDocumentFields } from '@/api/erp/wm/identity/types';

export interface TransferQuery extends PageQuery {
  transferNo?: string;
  fromWarehouseId?: number;
  toWarehouseId?: number;
  status?: string;
}

export interface TransferLineVO extends TraceDocumentFields {
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

export interface TransferVO extends TraceDocumentFields {
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
  selectFlowId?: number;
  remark?: string;
  wmTransferLineList?: TransferLineVO[];
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
}

export interface TransferForm extends TraceDocumentFields {
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
  selectFlowId?: number;
  remark?: string;
  wmTransferLineList?: TransferLineVO[];
}
