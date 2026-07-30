package com.nexus.erp.fin.arap.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.erp.fin.arap.domain.FinReceipt;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 收付款单视图对象 fin_receipt
 *
 * @author nexus
 * @date 2026-07-30
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = FinReceipt.class)
public class FinReceiptVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @ExcelProperty(value = "收付款单ID")
    private Long receiptId;

    @ExcelProperty(value = "收付款单号")
    private String receiptNo;

    @ExcelProperty(value = "收付款类型")
    private String receiptType;

    @ExcelProperty(value = "方向")
    private String direction;

    /** 客户ID(direction=AR) */
    private Long clientId;

    @ExcelProperty(value = "客户名称")
    private String clientName;

    /** 供应商ID(direction=AP) */
    private Long vendorId;

    @ExcelProperty(value = "供应商名称")
    private String vendorName;

    @ExcelProperty(value = "付款方")
    private String payerName;

    @ExcelProperty(value = "金额")
    private BigDecimal amount;

    @ExcelProperty(value = "已核销")
    private BigDecimal settledAmount;

    /** 币种 */
    private String currency;

    /** 汇率 */
    private BigDecimal exchangeRate;

    /** 第三方流水号 */
    private String thirdPartyNo;

    /** 支付订单号(跨库关联 pay_order) */
    private String payOrderNumber;

    @ExcelProperty(value = "来源")
    private String source;

    @ExcelProperty(value = "状态")
    private String status;

    @ExcelProperty(value = "收付款日期")
    private Date receiptDate;

    /** 乐观锁版本号 */
    private Integer version;

    /** 备注 */
    private String remark;
}
