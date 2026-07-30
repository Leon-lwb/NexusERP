package com.nexus.erp.fin.arap.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 未清项视图(应收应付报表):发票/收付款的未核销余额。
 *
 * @author nexus
 * @date 2026-07-30
 */
@Data
public class OpenItemVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** INVOICE-发票 RECEIPT-收付款单 */
    private String itemType;

    /** 单据ID(发票/收付款单) */
    private Long itemId;

    /** 单据号(发票号/收付款单号) */
    private String itemNo;

    /** AR/AP */
    private String direction;

    /** 往来方ID(客户/供应商) */
    private Long partnerId;

    /** 往来方名称 */
    private String partnerName;

    /** 发票价税合计 / 收付款金额 */
    private BigDecimal totalAmount;

    /** 已核销金额 */
    private BigDecimal settledAmount;

    /** 未清余额 = total - settled */
    private BigDecimal openAmount;

    /** 单据日期(发票日期/收付款日期) */
    private Date itemDate;
}
