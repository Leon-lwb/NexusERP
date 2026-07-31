package com.nexus.erp.fin.cost.constant;

/**
 * 存货成本 COST 常量
 *
 * @author nexus
 * @date 2026-08-01
 */
public class CostConstants {

    private CostConstants() {
    }

    // ---------- 科目编码(映射: 物料类型 -> 存货科目) ----------
    /** 材料采购(采购在途) */
    public static final String ACCT_MATERIAL_PURCHASE = "1401";
    /** 原材料(物料类型=1 原材料/4 辅料) */
    public static final String ACCT_RAW_MATERIAL = "1403";
    /** 库存商品(物料类型=3 成品) */
    public static final String ACCT_FINISHED_GOODS = "1405";
    /** 半成品(物料类型=2) */
    public static final String ACCT_SEMI_FINISHED = "1406";
    /** 生产成本-直接材料 */
    public static final String ACCT_PRODUCTION_COST = "5001";
    /** 主营业务成本 */
    public static final String ACCT_COST_OF_SALES = "6401";
    /** 应付账款-暂估(GR/IR) */
    public static final String ACCT_AP_PROVISION = "2202";

    // ---------- 来源单据类型(库存流水 source_doc_type) ----------
    /** 采购入库单 */
    public static final String TX_SOURCE_RECEIVE = "WM_MATERIAL_RECEIVE";
    /** 销售出库单 */
    public static final String TX_SOURCE_SALES = "WM_SALES_ISSUE";
    /** 生产领料单 */
    public static final String TX_SOURCE_PRODUCTION_ISSUE = "WM_PRODUCTION_ISSUE";
    /** 杂项入库 */
    public static final String TX_SOURCE_MISC_RECPT = "WM_MISC_RECPT";
    /** 杂项出库 */
    public static final String TX_SOURCE_MISC_ISSUE = "WM_MISC_ISSUE";

    // ---------- 事务类型 ----------
    /** 采购入库 */
    public static final String TX_RECEIVE_IN = "RECEIVE_IN";
    /** 生产领料 */
    public static final String TX_PRODUCTION_ISSUE = "PRODUCTION_ISSUE";
    /** 销售出库 */
    public static final String TX_SALES_OUT = "SALES_OUT";
    /** 杂项入库 */
    public static final String TX_MISC_IN = "MISC_IN";
    /** 杂项出库 */
    public static final String TX_MISC_OUT = "MISC_OUT";
}
