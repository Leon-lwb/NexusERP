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
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SoSalesOrderHeader.class)
public class SoSalesOrderHeaderVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 销售订单ID */
    private Long soId;

    /** 销售订单编号 */
    @ExcelProperty(value = "销售订单编号")
    private String soNo;

    /** 客户订单ID */
    private Long coId;
    /** 客户ID */
    private Long clientId;

    /** 客户名称 */
    @ExcelProperty(value = "客户名称")
    private String clientName;

    /** 销售代表ID */
    private Long salesRepId;

    /** 销售代表名称 */
    @ExcelProperty(value = "销售代表名称")
    private String salesRepName;

    /** 订单生成日期 */
    @ExcelProperty(value = "订单生成日期")
    private Date orderDate;

    /** 承诺交货日 */
    @ExcelProperty(value = "承诺交货日")
    private Date promiseDate;

    /** 客户要求交货日 */
    @ExcelProperty(value = "客户要求交货日")
    private Date requestedDate;

    /** 送货地址ID */
    private Long deliveryAddressId;
    /** 发票地址ID */
    private Long invoiceAddressId;
    /** 送货地址 */
    private String deliveryAddress;
    /** 发票地址 */
    private String invoiceAddress;

    /** 付款条款 */
    @ExcelProperty(value = "付款条款")
    private String paymentTerms;

    /** 货币 */
    @ExcelProperty(value = "货币")
    private String currency;

    /** 汇率 */
    @ExcelProperty(value = "汇率")
    private BigDecimal exchangeRate;

    /** 含税总金额 */
    @ExcelProperty(value = "含税总金额")
    private BigDecimal totalAmount;

    /** 税款 */
    @ExcelProperty(value = "税款")
    private BigDecimal taxAmount;

    /** 整单折扣 */
    @ExcelProperty(value = "整单折扣")
    private BigDecimal discountAmount;

    /** 整单折扣率 */
    @ExcelProperty(value = "整单折扣率")
    private BigDecimal discountRate;

    /** 信用状态 */
    @ExcelProperty(value = "信用状态")
    private String creditStatus;

    /** 信用检查时间 */
    private Date creditCheckTime;
    /** 信用锁定人 */
    private Long creditBlockedBy;

    /** 订单状态 */
    @ExcelProperty(value = "订单状态")
    private String orderStatus;

    /** 项目ID */
    private Long projectId;

    /** 项目配套发料 */
    @ExcelProperty(value = "项目配套发料")
    private Integer isPaEnabled;

    /** 确认时间 */
    private Date confirmedTime;
    /** 履行时间 */
    private Date fulfilledTime;
    /** 关闭时间 */
    private Date closedTime;
    /** 取消时间 */
    private Date cancelledTime;

    /** 取消原因 */
    @ExcelProperty(value = "取消原因")
    private String cancelledReason;

    /** 销售组织ID */
    private Long salesOrgId;

    /** 分销渠道 */
    @ExcelProperty(value = "分销渠道")
    private String distributionChannel;

    /** 客户参考号 */
    @ExcelProperty(value = "客户参考号")
    private String customerRefNo;

    /** 备注 */
    private String remark;

    /** 销售订单行列表 */
    private List<SoSalesOrderLineVo> soSalesOrderLineList;

    /** 审批流程ID */
    private Long selectFlowId;
}
