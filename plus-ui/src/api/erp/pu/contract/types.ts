export interface PuContractVo {
  contractId: number;
  contractNo: string;
  contractName: string;
  vendorId: number;
  vendorCode: string;
  vendorName: string;
  contractType: string;
  signDate: string;
  effectiveDate: string;
  expiryDate: string;
  totalAmount: number;
  currency: string;
  paymentTerms: string;
  status: string;
  selectFlowId: number;
  remark: string;
  createTime: string;
  puContractLineList: PuContractLineVo[];
}

export interface PuContractLineVo {
  lineId: number;
  contractId: number;
  materialId: number;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  unit: string;
  unitPrice: number;
  quantity: number;
  lineAmount: number;
  deliveryDate: string;
  sortOrder: number;
  remark: string;
}

export interface PuContractBo {
  contractId?: number;
  contractNo?: string;
  contractName?: string;
  vendorId?: number;
  vendorCode?: string;
  vendorName?: string;
  contractType?: string;
  signDate?: string;
  effectiveDate?: string;
  expiryDate?: string;
  totalAmount?: number;
  currency?: string;
  paymentTerms?: string;
  status?: string;
  selectFlowId?: number;
  remark?: string;
  puContractLineList?: PuContractLineVo[];
}

export interface PuContractQuery {
  contractNo?: string;
  contractName?: string;
  vendorId?: number;
  vendorName?: string;
  status?: string;
  contractType?: string;
  pageNum?: number;
  pageSize?: number;
}
