// 雪花 ID（64 位 Long）后端经 BigNumberSerializer 一律序列化为字符串，前端类型按 string 声明，避免精度截断
export interface PuContractVo {
  contractId: string;
  contractNo: string;
  contractName: string;
  vendorId: string;
  vendorCode: string;
  vendorName: string;
  contractType: string;
  signDate: string;
  effectiveDate: string;
  expiryDate: string;
  totalAmount: number;
  /** 税额合计（服务端按行税额汇总） */
  taxAmount: number;
  /** 不含税总金额（服务端按行不含税金额汇总） */
  amountExcludingTax: number;
  currency: string;
  paymentTerms: string;
  status: string;
  /**
   * 正文签署状态（来自 es_sign_document.sign_status：0未签 1部分签 2已签）
   * <p>pu_contract 表没有这一列，由后端列表查询批量补充；该合同尚无正文时为 null。</p>
   */
  docSignStatus?: string;
  selectFlowId: string;
  remark: string;
  createTime: string;
  puContractLineList: PuContractLineVo[];
}

export interface PuContractLineVo {
  lineId: string;
  contractId: string;
  materialId: string;
  materialCode: string;
  materialName: string;
  materialSpec: string;
  unit: string;
  unitPrice: number;
  /** 税率(%) */
  taxRate?: number;
  quantity: number;
  lineAmount: number;
  /** 税额（服务端按 行金额×税率÷100 重算） */
  taxAmount?: number;
  /** 不含税行金额（= 行金额 − 税额） */
  lineAmountExcludingTax?: number;
  deliveryDate: string;
  sortOrder: number;
  remark: string;
}

export interface PuContractBo {
  contractId?: string;
  contractNo?: string;
  contractName?: string;
  vendorId?: string;
  vendorCode?: string;
  vendorName?: string;
  contractType?: string;
  signDate?: string;
  effectiveDate?: string;
  expiryDate?: string;
  totalAmount?: number;
  taxAmount?: number;
  amountExcludingTax?: number;
  currency?: string;
  paymentTerms?: string;
  status?: string;
  selectFlowId?: string;
  remark?: string;
  puContractLineList?: PuContractLineVo[];
}

export interface PuContractQuery {
  contractNo?: string;
  contractName?: string;
  vendorId?: string;
  vendorName?: string;
  status?: string;
  contractType?: string;
  pageNum?: number;
  pageSize?: number;
}
