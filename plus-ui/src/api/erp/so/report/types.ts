export interface SalesReportQuery {
  months?: number;
  topN?: number;
}

/**
 * 销售执行率报表行（对应后端 `getSalesExecution`）。
 * <p>已取消（CANCELLED）订单不计入金额，也不计入完成率分母。</p>
 */
export interface SalesExecutionRow {
  /** 月份（yyyy-MM） */
  month: string;
  /** 订单数 */
  orderCount: number;
  /** 销售额合计 */
  totalAmount: number;
  /** 已完成订单数（FULFILLED / CLOSED） */
  completedCount: number;
  /** 完成率（百分数，后端已 ×100 并保留 1 位小数） */
  completionRate: number;
}

/** 客户销售排行报表行（对应后端 `getCustomerRanking`） */
export interface CustomerRankingRow {
  /** 客户名称 */
  clientName: string;
  /** 订单数 */
  orderCount: number;
  /** 销售额合计 */
  totalAmount: number;
}
