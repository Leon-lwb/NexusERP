package com.nexus.erp.fin.fa.constant;

/**
 * 固定资产 FA 常量
 *
 * @author nexus
 * @date 2026-08-01
 */
public class FaConstants {

    private FaConstants() {
    }

    // ---------- 资产状态 ----------
    /** 使用中 */
    public static final String ASSET_STATUS_IN_USE = "IN_USE";
    /** 在建(未转固) */
    public static final String ASSET_STATUS_CWIP = "CWIP";
    /** 已处置 */
    public static final String ASSET_STATUS_DISPOSED = "DISPOSED";

    // ---------- 折旧方法 ----------
    /** 直线法(平均年限法) */
    public static final String METHOD_STRAIGHT_LINE = "STRAIGHT_LINE";
    /** 双倍余额递减法 */
    public static final String METHOD_DOUBLE_DECLINING = "DOUBLE_DECLINING";
    /** 年数总和法 */
    public static final String METHOD_SUM_OF_YEARS = "SUM_OF_YEARS";
    /** 手工(不自动计提) */
    public static final String METHOD_MANUAL = "MANUAL";

    // ---------- 折旧记录状态 ----------
    /** 计划(待过账) */
    public static final String DEP_STATUS_PLANNED = "PLANNED";
    /** 已过账 */
    public static final String DEP_STATUS_DONE = "DONE";

    // ---------- 处置类型 ----------
    /** 出售 */
    public static final String DISPOSAL_SALE = "SALE";
    /** 报废 */
    public static final String DISPOSAL_SCRAP = "SCRAP";
    /** 处置记录状态 */
    public static final String DISPOSAL_STATUS_POSTED = "POSTED";

    // ---------- 科目编码 ----------
    /** 固定资产(原值) */
    public static final String ACCT_FA_ORIGINAL = "1601";
    /** 累计折旧 */
    public static final String ACCT_FA_DEPRECIATION = "1602";
    /** 银行存款 */
    public static final String ACCT_BANK = "1002";
    /** 管理费用(折旧费用默认) */
    public static final String ACCT_ADMIN_EXPENSE = "6602";
    /** 营业外支出(处置损失默认) */
    public static final String ACCT_NON_OPER_EXPENSE = "6711";
    /** 营业外收入(处置收益默认) */
    public static final String ACCT_NON_OPER_INCOME = "6301";

    // ---------- 幂等键 ----------
    /** 折旧批次来源单据号前缀(用于凭证摘要/来源号) */
    public static final String SOURCE_NO_DEPRECIATION = "DEP-";
    /** 处置来源单据号前缀 */
    public static final String SOURCE_NO_DISPOSAL = "DISP-";
}
