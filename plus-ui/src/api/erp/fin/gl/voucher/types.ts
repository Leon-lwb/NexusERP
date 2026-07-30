/** 记账凭证查询参数 */
export interface VoucherQuery extends PageQuery {
  voucherNo?: string;
  status?: string;
  voucherSource?: string;
  periodId?: number;
}

/** 记账凭证视图对象 */
export interface VoucherVO {
  voucherId: number;
  voucherNo: string;
  voucherWord: string;
  voucherDate: string;
  periodId?: number;
  voucherSource: string;
  sourceId?: number;
  sourceNo?: string;
  attachmentCount?: number;
  totalDebit: number;
  totalCredit: number;
  currency?: string;
  status: string;
  postedBy?: number;
  postedTime?: string;
  remark?: string;
  voucherLineList?: VoucherLineVO[];
}

/** 凭证分录行视图对象 */
export interface VoucherLineVO {
  lineId?: number;
  voucherId?: number;
  lineNo?: number;
  accountId: number;
  accountCode?: string;
  accountName?: string;
  summary?: string;
  debit?: number;
  credit?: number;
  currency?: string;
  amountCurrency?: number;
  exchangeRate?: number;
  partnerId?: number;
  partnerType?: string;
}

/** 记账凭证表单对象 */
export interface VoucherForm {
  voucherId?: number;
  voucherNo?: string;
  voucherWord?: string;
  voucherDate: string;
  voucherSource?: string;
  sourceId?: number;
  sourceNo?: string;
  attachmentCount?: number;
  currency?: string;
  remark?: string;
  voucherLineList: VoucherLineForm[];
}

/** 凭证分录行表单对象 */
export interface VoucherLineForm {
  lineId?: number;
  lineNo?: number;
  accountId: number;
  accountCode?: string;
  accountName?: string;
  summary?: string;
  debit?: number;
  credit?: number;
  currency?: string;
  amountCurrency?: number;
  exchangeRate?: number;
  partnerId?: number;
  partnerType?: string;
}
