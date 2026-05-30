export interface VendorEvaluationVO {
  evaluationId?: number;
  vendorId?: number;
  vendorCode?: string;
  vendorName?: string;
  evalPeriod?: string;
  deliveryRate?: number;
  qualityRate?: number;
  priceScore?: number;
  totalScore?: number;
  evalLevel?: string;
  totalOrders?: number;
  onTimeOrders?: number;
  totalReceivedQty?: number;
  qualifiedQty?: number;
  returnQty?: number;
  evalDate?: string;
  remark?: string;
}

export interface VendorEvaluationQuery {
  pageNum?: number;
  pageSize?: number;
  vendorName?: string;
  evalPeriod?: string;
  evalLevel?: string;
}

export interface VendorEvaluationForm {
  evaluationId?: number;
  vendorId?: number;
  evalPeriod?: string;
  remark?: string;
}
