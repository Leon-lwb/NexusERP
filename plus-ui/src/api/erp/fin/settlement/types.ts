/** 核销记录查询参数 */
export interface FinSettlementQuery extends PageQuery {
  direction?: string;
  invoiceNo?: string;
  invoiceId?: string;
  receiptId?: string;
}

/** 核销记录视图对象 */
export interface FinSettlementVO {
  settlementId: string;
  settlementNo: string;
  direction: string;
  invoiceId: string;
  invoiceNo?: string;
  invoiceType?: string;
  receiptId: string;
  receiptNo?: string;
  settleAmount: number;
  settleDate?: string;
  operator?: string;
  remark?: string;
}

/** 核销操作入参 */
export interface FinSettlementForm {
  direction: string;
  invoiceId: string;
  receiptId: string;
  settleAmount: number;
  settleDate?: string;
  remark?: string;
}

/** 未清项视图(应收应付报表/核销选单) */
export interface OpenItemVO {
  itemType: string;
  itemId: string;
  itemNo: string;
  direction: string;
  partnerId?: string;
  partnerName?: string;
  totalAmount: number;
  settledAmount: number;
  openAmount: number;
  itemDate?: string;
}
