export interface ReturnQuery extends PageQuery {
  returnNo?: string;
  soNo?: string;
  clientId?: number;
  clientName?: string;
  status?: string;
}

export interface ReturnLineVO {
  lineId?: number;
  returnId?: number;
  soLineId?: number;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  baseUomId?: string;
  baseUomName?: string;
  returnQuantity?: number;
  unitPrice?: number;
  amount?: number;
  taxRate?: number;
  taxAmount?: number;
  returnReason?: string;
  sortOrder?: number;
  remark?: string;
}

export interface ReturnVO {
  returnId?: number;
  returnNo?: string;
  soId?: number;
  soNo?: string;
  clientId?: number;
  clientName?: string;
  salesRepId?: number;
  salesRepName?: string;
  returnDate?: string;
  returnReason?: string;
  returnType?: string;
  totalAmount?: number;
  taxAmount?: number;
  refundAmount?: number;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  status?: string;
  selectFlowId?: number;
  remark?: string;
  soReturnLineList?: ReturnLineVO[];
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
}

export interface ReturnForm {
  returnId?: number;
  returnNo: string;
  soId?: number;
  soNo?: string;
  clientId?: number;
  clientName?: string;
  salesRepId?: number;
  salesRepName?: string;
  returnDate?: string;
  returnReason?: string;
  returnType?: string;
  totalAmount?: number;
  taxAmount?: number;
  refundAmount?: number;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  status?: string;
  selectFlowId?: number;
  remark?: string;
  soReturnLineList?: ReturnLineVO[];
}
