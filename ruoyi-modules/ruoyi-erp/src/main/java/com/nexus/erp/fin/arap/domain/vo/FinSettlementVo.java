package com.nexus.erp.fin.arap.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.erp.fin.arap.domain.FinSettlement;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 核销记录视图对象 fin_settlement
 *
 * @author nexus
 * @date 2026-07-30
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = FinSettlement.class)
public class FinSettlementVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "核销记录ID")
    private Long settlementId;

    @ExcelProperty(value = "核销单号")
    private String settlementNo;

    @ExcelProperty(value = "方向")
    private String direction;

    /** 发票ID */
    private Long invoiceId;

    @ExcelProperty(value = "发票号")
    private String invoiceNo;

    /** 发票类型 */
    private String invoiceType;

    /** 收付款单ID */
    private Long receiptId;

    /** 收付款单号(冗余,Service 查询时填充) */
    @ExcelProperty(value = "收付款单号")
    private String receiptNo;

    @ExcelProperty(value = "核销金额")
    private BigDecimal settleAmount;

    @ExcelProperty(value = "核销日期")
    private Date settleDate;

    /** 操作人 */
    private String operator;

    /** 备注 */
    private String remark;
}
