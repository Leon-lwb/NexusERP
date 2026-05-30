package com.nexus.erp.so.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.so.domain.SoSalesOrderHeader;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售订单业务对象 so_sales_order_header
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SoSalesOrderHeader.class, reverseConvertGenerate = false)
public class SoSalesOrderHeaderBo extends BaseEntity {

    /** 销售订单ID */
    private Long soId;

    /** 销售订单编号 */
    @NotBlank(message = "销售订单编号不能为空")
    private String soNo;

    /** 客户订单ID */
    private Long coId;
    /** 客户ID */
    private Long clientId;
    /** 客户名称 */
    private String clientName;
    /** 销售代表ID */
    private Long salesRepId;
    /** 销售代表名称 */
    private String salesRepName;
    /** 订单日期 */
    private Date orderDate;
    /** 承诺交货日期 */
    private Date promiseDate;
    /** 客户要求交货日期 */
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
    private String paymentTerms;
    /** 币种 */
    private String currency;
    /** 汇率 */
    private BigDecimal exchangeRate;
    /** 总金额 */
    private BigDecimal totalAmount;
    /** 税额 */
    private BigDecimal taxAmount;
    /** 折扣金额 */
    private BigDecimal discountAmount;
    /** 折扣率 */
    private BigDecimal discountRate;
    /** 信用状态 */
    private String creditStatus;
    /** 信用检查时间 */
    private Date creditCheckTime;
    /** 信用锁定人 */
    private Long creditBlockedBy;
    /** 订单状态 */
    private String orderStatus;
    /** 项目ID */
    private Long projectId;
    /** 是否启用项目配套发料 */
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
    private String cancelledReason;
    /** 销售组织ID */
    private Long salesOrgId;
    /** 分销渠道 */
    private String distributionChannel;
    /** 客户参考号 */
    private String customerRefNo;
    /** 扩展字段1 */
    private String attr1;
    /** 扩展字段2 */
    private String attr2;
    /** 扩展字段3 */
    private Long attr3;
    /** 扩展字段4 */
    private Long attr4;
    /** 备注 */
    private String remark;

    /** 审批流程ID */
    private Long selectFlowId;

    /** 销售订单行列表 */
    private List<SoSalesOrderLineBo> soSalesOrderLineList;

    /** ID数组（用于批量操作） */
    private Long[] ids;
}
