package com.nexus.erp.fin.gl.constant;

/**
 * GL 总账常量
 *
 * @author nexus
 * @date 2026-07-29
 */
public class GlConstants {

    /** 凭证状态:草稿 */
    public static final String VOUCHER_STATUS_DRAFT = "DRAFT";
    /** 凭证状态:已过账 */
    public static final String VOUCHER_STATUS_POSTED = "POSTED";
    /** 凭证状态:已冲销 */
    public static final String VOUCHER_STATUS_CANCELLED = "CANCELLED";

    /** 凭证来源:手工录入 */
    public static final String SOURCE_MANUAL = "MANUAL";
    /** 凭证来源:销售发票 */
    public static final String SOURCE_SO_INVOICE = "SO_INVOICE";
    /** 凭证来源:采购发票 */
    public static final String SOURCE_PU_INVOICE = "PU_INVOICE";
    /** 凭证来源:收款单 */
    public static final String SOURCE_RECEIPT = "RECEIPT";
    /** 凭证来源:折旧计提 */
    public static final String SOURCE_DEPRECIATION = "DEPRECIATION";
    /** 固定资产处置(FaDisposalServiceImpl 过账; FIN-FA-03: 处置不再复用 DEPRECIATION) */
    public static final String SOURCE_FA_DISPOSAL = "FA_DISPOSAL";
    /** 凭证来源:成本核算 */
    public static final String SOURCE_COST = "COST";
    /** 凭证来源:调整 */
    public static final String SOURCE_ADJUSTMENT = "ADJUSTMENT";

    /** 期间状态:开放 */
    public static final String PERIOD_OPEN = "OPEN";
    /** 期间状态:已结账 */
    public static final String PERIOD_CLOSED = "CLOSED";
    /** 期间状态:已锁定 */
    public static final String PERIOD_LOCKED = "LOCKED";

    /** 统驭类型:应收 */
    public static final String RECON_AR = "AR";
    /** 统驭类型:应付 */
    public static final String RECON_AP = "AP";
    /** 统驭类型:固定资产 */
    public static final String RECON_FA = "FA";

    /** 通用:是 */
    public static final String YES = "1";
    /** 通用:否 */
    public static final String NO = "0";

    private GlConstants() {
    }
}
