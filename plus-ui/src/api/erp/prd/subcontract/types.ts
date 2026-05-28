export interface PrdSubcontractVO {
  subcontractId: number;
  subcontractNo: string;
  vendorId: number;
  vendorCode: string;
  vendorName: string;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  baseUomName: string;
  plannedQty: number;
  actualQty: number;
  unitPrice: number;
  totalAmount: number;
  planStartDate: string;
  planEndDate: string;
  actualStartDate: string;
  actualEndDate: string;
  woId: number;
  woNo: string;
  status: string;
  remark: string;
  createTime: string;
}

export interface PrdSubcontractQuery extends PageQuery {
  subcontractNo?: string;
  vendorName?: string;
  materialCode?: string;
  materialName?: string;
  status?: string;
}

export interface PrdSubcontractForm {
  subcontractId?: number;
  subcontractNo?: string;
  vendorId?: number;
  vendorCode?: string;
  vendorName?: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  baseUomName?: string;
  plannedQty?: number;
  unitPrice?: number;
  planStartDate?: string;
  planEndDate?: string;
  remark?: string;
}
