import type { TraceDocumentFields } from '@/api/erp/wm/identity/types';

export interface SalesIssueQuery extends PageQuery {
  issueCode?: string;
  issueName?: string;
  soNo?: string;
  clientName?: string;
  warehouseName?: string;
  status?: string;
  issueDate?: string;
}

export interface SalesIssueVO extends TraceDocumentFields {
  issueId: number;
  issueCode: string;
  issueName: string;
  soId: number;
  soNo: string;
  clientId: number;
  clientCode: string;
  clientName: string;
  warehouseId: number;
  warehouseCode: string;
  warehouseName: string;
  issueDate: string;
  totalQuantity: number;
  totalAmount: number;
  status: string;
  remark: string;
  selectFlowId: number;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
  wmSalesIssueLineList?: SalesIssueLineVO[];
}

export interface SalesIssueLineVO extends TraceDocumentFields {
  lineId: number;
  issueId: number;
  sourceDocLineId: number;
  lineNo: string;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  baseUomId: string;
  baseUomName: string;
  batchId: number;
  batchCode: string;
  locationId: number;
  locationCode: string;
  locationName: string;
  quantityIssue: number;
  quantityOrdered: number;
  quantityDelivered: number;
  unitPrice: number;
  lineAmount: number;
  remark: string;
  stockQuantity?: number;
}

export interface SalesIssueForm extends TraceDocumentFields {
  issueId?: number;
  issueCode?: string;
  issueName?: string;
  soId?: number;
  soNo?: string;
  clientId?: number;
  clientCode?: string;
  clientName?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  issueDate?: string;
  totalQuantity?: number | string;
  totalAmount?: number | string;
  status?: string;
  remark?: string;
  selectFlowId?: number | string;
  wmSalesIssueLineList?: any[];
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
  [key: string]: any;
}
