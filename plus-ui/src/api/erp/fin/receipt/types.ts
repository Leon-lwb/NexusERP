/** 收付款单查询参数 */
export interface FinReceiptQuery extends PageQuery {
  receiptNo?: string;
  direction?: string;
  status?: string;
  source?: string;
  clientId?: string;
  vendorId?: string;
}

/** 收付款单视图对象 */
export interface FinReceiptVO {
  receiptId: string;
  receiptNo: string;
  receiptType?: string;
  direction: string;
  clientId?: string;
  clientName?: string;
  vendorId?: string;
  vendorName?: string;
  payerName?: string;
  amount: number;
  settledAmount?: number;
  currency?: string;
  payOrderNumber?: string;
  source?: string;
  status: string;
  receiptDate?: string;
  remark?: string;
}

/** 收付款单表单对象 */
export interface FinReceiptForm {
  receiptId?: string;
  receiptNo?: string;
  receiptType?: string;
  direction: string;
  clientId?: string;
  vendorId?: string;
  payerName?: string;
  amount: number;
  currency?: string;
  receiptDate?: string;
  remark?: string;
}
