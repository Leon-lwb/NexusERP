/** 币种汇率查询参数 */
export interface CurrencyRateQuery extends PageQuery {
  currency?: string;
  rateType?: string;
}

/** 币种汇率视图对象 */
export interface CurrencyRateVO {
  rateId: number;
  currency: string;
  baseCurrency?: string;
  rateDate: string;
  rate: number;
  rateType?: string;
}

/** 币种汇率表单对象 */
export interface CurrencyRateForm {
  rateId?: number;
  currency: string;
  baseCurrency?: string;
  rateDate: string;
  rate: number;
  rateType?: string;
}
