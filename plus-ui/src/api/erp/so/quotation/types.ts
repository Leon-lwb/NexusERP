export interface QuotationQuery extends PageQuery {
  quotationNo?: string;
  clientId?: number;
  clientName?: string;
  status?: string;
}

export interface QuotationLineVO {
  lineId?: number;
  quotationId?: number;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  materialSpec?: string;
  baseUomId?: string;
  baseUomName?: string;
  quantity?: number;
  unitPrice?: number;
  amount?: number;
  taxRate?: number;
  taxAmount?: number;
  deliveryDate?: string;
  sortOrder?: number;
  remark?: string;
}

export interface QuotationVO {
  quotationId?: number;
  quotationNo?: string;
  clientId?: number;
  clientName?: string;
  salesRepId?: number;
  salesRepName?: string;
  quotationDate?: string;
  validUntil?: string;
  currency?: string;
  exchangeRate?: number;
  totalAmount?: number;
  taxAmount?: number;
  discountAmount?: number;
  discountRate?: number;
  paymentTerms?: string;
  deliveryTerms?: string;
  status?: string;
  selectFlowId?: number;
  remark?: string;
  soQuotationLineList?: QuotationLineVO[];
  createBy?: number;
  createTime?: string;
  updateBy?: number;
  updateTime?: string;
}

export interface QuotationForm {
  quotationId?: number;
  quotationNo: string;
  clientId?: number;
  clientName?: string;
  salesRepId?: number;
  salesRepName?: string;
  quotationDate?: string;
  validUntil?: string;
  currency?: string;
  exchangeRate?: number;
  totalAmount?: number;
  taxAmount?: number;
  discountAmount?: number;
  discountRate?: number;
  paymentTerms?: string;
  deliveryTerms?: string;
  status?: string;
  remark?: string;
  soQuotationLineList?: QuotationLineVO[];
}
