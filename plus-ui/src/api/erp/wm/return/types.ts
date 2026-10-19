import type { TraceDocumentFields } from '@/api/erp/wm/identity/types';

export interface ReturnQuery extends PageQuery {
  returnNo?: string;
  returnType?: string;
  status?: string;
}

export interface ReturnLineVO extends TraceDocumentFields {
  lineId?: number;
  returnId?: number;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  baseUomId?: string;
  baseUomName?: string;
  batchId?: number;
  batchCode?: string;
  locationId?: number;
  locationCode?: string;
  locationName?: string;
  /** 计划退料数量 */
  quantity?: number;
  /** PDA 已核销数量 */
  actualQty?: number;
  remark?: string;
}

export interface ReturnVO extends TraceDocumentFields {
  returnId?: number;
  returnNo?: string;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  /** GOOD/DEFECT/SCRAP */
  returnType?: string;
  sourceDoc?: string;
  /** DRAFT/COMPLETED */
  status?: string;
  returnDate?: string;
  remark?: string;
  lines?: ReturnLineVO[];
}

export interface ReturnForm extends TraceDocumentFields {
  returnId?: number;
  warehouseId?: number;
  warehouseCode?: string;
  warehouseName?: string;
  returnType?: string;
  sourceDoc?: string;
  returnDate?: string;
  remark?: string;
  lines?: ReturnLineVO[];
}
