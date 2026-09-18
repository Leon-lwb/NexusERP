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
  /**
   * 正文签署状态（来自 es_sign_document.sign_status：0未签 1部分签 2已签）
   * <p>pu_contract 表没有这一列，由后端列表查询批量补充；该合同尚无正文时为 null。</p>
   */
  docSignStatus?: string;
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
