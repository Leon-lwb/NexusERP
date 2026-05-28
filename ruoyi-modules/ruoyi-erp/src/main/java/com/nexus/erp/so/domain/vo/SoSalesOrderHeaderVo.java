package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoSalesOrderHeader;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售订单视图对象 so_sales_order_header
 *
 * @author ruoyi
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SoSalesOrderHeader.class)
public class SoSalesOrderHeaderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long soId;

    @ExcelProperty(value = "销售订单编号")
    private String soNo;

    private Long coId;
    private Long clientId;

    @ExcelProperty(value = "客户名称")
    private String clientName;

    private Long salesRepId;

    @ExcelProperty(value = "销售代表名称")
    private String salesRepName;

    @ExcelProperty(value = "订单生成日期")
    private Date orderDate;

    @ExcelProperty(value = "承诺交货日")
    private Date promiseDate;

    @ExcelProperty(value = "客户要求交货日")
    private Date requestedDate;

    private Long deliveryAddressId;
    private Long invoiceAddressId;
    private String deliveryAddress;
    private String invoiceAddress;

    @ExcelProperty(value = "付款条款")
    private String paymentTerms;

    @ExcelProperty(value = "货币")
    private String currency;

    @ExcelProperty(value = "汇率")
    private BigDecimal exchangeRate;

    @ExcelProperty(value = "含税总金额")
    private BigDecimal totalAmount;

    @ExcelProperty(value = "税款")
    private BigDecimal taxAmount;

    @ExcelProperty(value = "整单折扣")
    private BigDecimal discountAmount;

    @ExcelProperty(value = "整单折扣率")
    private BigDecimal discountRate;

    @ExcelProperty(value = "信用状态")
    private String creditStatus;

    private Date creditCheckTime;
    private Long creditBlockedBy;

    @ExcelProperty(value = "订单状态")
    private String orderStatus;

    private Long projectId;

    @ExcelProperty(value = "项目配套发料")
    private Integer isPaEnabled;

    private Date confirmedTime;
    private Date fulfilledTime;
    private Date closedTime;
    private Date cancelledTime;

    @ExcelProperty(value = "取消原因")
    private String cancelledReason;

    private Long salesOrgId;

    @ExcelProperty(value = "分销渠道")
    private String distributionChannel;

    @ExcelProperty(value = "客户参考号")
    private String customerRefNo;

    private String remark;

    private List<SoSalesOrderLineVo> soSalesOrderLineList;

    private Long selectFlowId;
}
