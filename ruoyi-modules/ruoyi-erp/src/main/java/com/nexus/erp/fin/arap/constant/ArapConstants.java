package com.nexus.erp.fin.arap.constant;

/**
 * AR/AP 核销常量
 *
 * @author nexus
 * @date 2026-07-30
 */
public class ArapConstants {

    /** 方向:应收收款 */
    public static final String DIRECTION_AR = "AR";
    /** 方向:应付付款 */
    public static final String DIRECTION_AP = "AP";

    /** 收付款单状态:草稿 */
    public static final String RECEIPT_STATUS_DRAFT = "DRAFT";
    /** 收付款单状态:已确认 */
    public static final String RECEIPT_STATUS_CONFIRMED = "CONFIRMED";
    /** 收付款单状态:已结算(全额核销) */
    public static final String RECEIPT_STATUS_SETTLED = "SETTLED";
    /** 收付款单状态:已作废 */
    public static final String RECEIPT_STATUS_CANCELLED = "CANCELLED";

    /** 收付款来源:手工录入 */
    public static final String SOURCE_MANUAL = "MANUAL";
    /** 收付款来源:支付回调 */
    public static final String SOURCE_PAY_CALLBACK = "PAY_CALLBACK";

    /** 往来类型:客户 */
    public static final String PARTNER_CLIENT = "CLIENT";
    /** 往来类型:供应商 */
    public static final String PARTNER_VENDOR = "VENDOR";

    /** 未清项类型:发票 */
    public static final String ITEM_INVOICE = "INVOICE";
    /** 未清项类型:收付款单 */
    public static final String ITEM_RECEIPT = "RECEIPT";

    // ===== GL 科目编码(F0 种子已预置,poster 按编码查 account_id) =====
    /** 银行存款(收付款对方科目) */
    public static final String ACCT_BANK = "1002";
    /** 应收账款(统驭 AR) */
    public static final String ACCT_AR = "1122";
    /** 应付账款(统驭 AP) */
    public static final String ACCT_AP = "2202";
    /** 库存商品(采购入账借方) */
    public static final String ACCT_INVENTORY = "1405";
    /** 应交增值税-销项税额 */
    public static final String ACCT_TAX_OUTPUT = "2221.01";
    /** 应交增值税-进项税额 */
    public static final String ACCT_TAX_INPUT = "2221.02";
    /** 主营业务收入 */
    public static final String ACCT_REVENUE = "6001";

    private ArapConstants() {
    }
}
