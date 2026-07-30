package com.nexus.erp.fin.gl.constant;

/**
 * GL 总账常量
 *
 * @author nexus
 * @date 2026-07-29
 */
public class GlConstants {

    /** 凭证状态 */
    public static final String VOUCHER_STATUS_DRAFT = "DRAFT";
    public static final String VOUCHER_STATUS_POSTED = "POSTED";
    public static final String VOUCHER_STATUS_CANCELLED = "CANCELLED";

    /** 凭证来源 */
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_SO_INVOICE = "SO_INVOICE";
    public static final String SOURCE_PU_INVOICE = "PU_INVOICE";
    public static final String SOURCE_RECEIPT = "RECEIPT";
    public static final String SOURCE_DEPRECIATION = "DEPRECIATION";
    public static final String SOURCE_COST = "COST";
    public static final String SOURCE_ADJUSTMENT = "ADJUSTMENT";

    /** 期间状态 */
    public static final String PERIOD_OPEN = "OPEN";
    public static final String PERIOD_CLOSED = "CLOSED";
    public static final String PERIOD_LOCKED = "LOCKED";

    /** 统驭类型 */
    public static final String RECON_AR = "AR";
    public static final String RECON_AP = "AP";
    public static final String RECON_FA = "FA";

    /** 通用是/否 */
    public static final String YES = "1";
    public static final String NO = "0";

    private GlConstants() {
    }
}
