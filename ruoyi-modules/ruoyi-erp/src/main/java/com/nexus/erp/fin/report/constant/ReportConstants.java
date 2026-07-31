package com.nexus.erp.fin.report.constant;

/**
 * F5 财务报表与期末关账常量
 *
 * @author nexus
 * @date 2026-07-31
 */
public class ReportConstants {

    /** 报表类型:资产负债表 */
    public static final String REPORT_BALANCE = "BALANCE";
    /** 报表类型:利润表 */
    public static final String REPORT_INCOME = "INCOME";
    /** 报表类型:现金流量表 */
    public static final String REPORT_CASH = "CASH";

    /** 关账类型:月结 */
    public static final String CLOSE_MONTHLY = "MONTHLY";
    /** 关账类型:年结 */
    public static final String CLOSE_YEARLY = "YEARLY";

    /** 关账步骤:试算校验 */
    public static final String STEP_TRIAL_CHECK = "TRIAL_CHECK";
    /** 关账步骤:损益结转 */
    public static final String STEP_CARRY_PL = "CARRY_PL";
    /** 关账步骤:外币重估 */
    public static final String STEP_REVALUATION = "REVALUATION";
    /** 关账步骤:报表快照 */
    public static final String STEP_REPORT_SNAPSHOT = "REPORT_SNAPSHOT";
    /** 关账步骤:关账 */
    public static final String STEP_CLOSE_PERIOD = "CLOSE_PERIOD";
    /** 关账步骤:期初结转(年结) */
    public static final String STEP_CARRY_FORWARD = "CARRY_FORWARD";

    /** 关账日志状态:成功 */
    public static final String LOG_SUCCESS = "SUCCESS";
    /** 关账日志状态:失败 */
    public static final String LOG_FAILED = "FAILED";

    /** 月结进度:试算已校验 */
    public static final String CLOSING_TRIAL = "CLOSING_TRIAL";
    /** 月结进度:损益已结转 */
    public static final String CLOSING_CARRY = "CLOSING_CARRY";
    /** 月结进度:重估已完成 */
    public static final String CLOSING_REVAL = "CLOSING_REVAL";
    /** 月结进度:已完成(CLOSED 冗余) */
    public static final String CLOSING_DONE = "CLOSING_DONE";

    /** 结转/重估凭证来源:调整(沿用 F0 SOURCE_ADJUSTMENT 语义) */
    public static final String VOUCHER_SOURCE = "ADJUSTMENT";

    /** 损益结转幂等键偏移:periodId*100+1(ADJUSTMENT, sourceId) */
    public static final long IDEMPOTENT_CARRY_PL = 1L;
    /** 外币重估幂等键偏移:periodId*100+2 */
    public static final long IDEMPOTENT_REVALUATION = 2L;
    /** 年结本年利润转未分配利润幂等键偏移:periodId*100+3 */
    public static final long IDEMPOTENT_YEARLY_PL = 3L;

    /** 本年利润科目编码 */
    public static final String ACCT_YEAR_PROFIT = "4103";
    /** 未分配利润科目编码 */
    public static final String ACCT_RETAINED_EARNINGS = "4104";
    /** 汇兑损益科目编码(外币重估差额) */
    public static final String ACCT_EXCHANGE_GAIN_LOSS = "6603.02";
    /** 本位币 */
    public static final String BASE_CURRENCY = "CNY";

    private ReportConstants() {
    }
}
