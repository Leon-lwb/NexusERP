package com.nexus.core.so.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 销售订单对象 so_sales_order_header
 *
 * @author nexus
 * @date 2025-10-22
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.so.mapper.SoSalesOrderHeaderMapper.SoSalesOrderHeaderResult")
public class SoSalesOrderHeader extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** 销售订单ID */
    @TableId(value = "so_id",type = IdType.AUTO)
    private Long soId;
    /** 销售订单编号 */
    @Excel(name = "销售订单编号")
    private String soNo;
    /** 来源客户订单 */
    @Excel(name = "来源客户订单")
    private Long coId;
    /** 来源客户ID */
    @Excel(name = "来源客户ID")
    private Long clientId;
    /** 销售代表 */
    @Excel(name = "销售代表")
    private Long salesRepId;
    /** 订单生成日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "订单生成日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date orderDate;
    /** 承诺交货日 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "承诺交货日", width = 30, dateFormat = "yyyy-MM-dd")
    private Date promiseDate;
    /** 客户要求交货日 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "客户要求交货日", width = 30, dateFormat = "yyyy-MM-dd")
    private Date requestedDate;
    /** 发货地址 */
    @Excel(name = "发货地址")
    private Long deliveryAddressId;
    /** 开票地址ID */
    @Excel(name = "开票地址ID")
    private Long invoiceAddressId;
    /** 发货地址详情 */
    private String deliveryAddress;
    /** 开票地址详情 */
    private String invoiceAddress;
    /** 付款条款 */
    @Excel(name = "付款条款")
    private String paymentTerms;
    /** 货币 */
    @Excel(name = "货币")
    private String currency;
    /** 汇率 */
    @Excel(name = "汇率")
    private BigDecimal exchangeRate;
    /** 含税总金额 */
    @Excel(name = "含税总金额")
    private BigDecimal totalAmount;
    /** 税款 */
    @Excel(name = "税款")
    private BigDecimal taxAmount;
    /** 整单折扣 */
    @Excel(name = "整单折扣")
    private BigDecimal discountAmount;
    /** 整单折扣率 */
    @Excel(name = "整单折扣率")
    private BigDecimal discountRate;
    /** 信用状态 */
    @Excel(name = "信用状态")
    private String creditStatus;
    /** 信用检查时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "信用检查时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date creditCheckTime;
    /** 信用拦截人 */
    @Excel(name = "信用拦截人")
    private Long creditBlockedBy;
    /** 订单状态 */
    @Excel(name = "订单状态")
    private String orderStatus;
    /** 项目ID */
    @Excel(name = "项目ID")
    private Long projectId;
    /** 是否启用项目配套发料 */
    @Excel(name = "是否启用项目配套发料")
    private Integer isPaEnabled;
    /** 确认时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "确认时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date confirmedTime;
    /** 完全履行时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "完全履行时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date fulfilledTime;
    /** 结束时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "结束时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date closedTime;
    /** 取消时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "取消时间", width = 30, dateFormat = "yyyy-MM-dd")
    private Date cancelledTime;
    /** 取消原因 */
    @Excel(name = "取消原因")
    private String cancelledReason;
    /** 销售组织ID */
    @Excel(name = "销售组织ID")
    private Long salesOrgId;
    /** 分销渠道 */
    @Excel(name = "分销渠道")
    private String distributionChannel;
    /** 客户参考号 */
    @Excel(name = "客户参考号")
    private String customerRefNo;
    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Long attr3;
    /** 预留字段4 */
    private Long attr4;
}