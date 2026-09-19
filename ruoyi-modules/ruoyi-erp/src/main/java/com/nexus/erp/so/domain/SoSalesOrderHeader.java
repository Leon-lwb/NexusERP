package com.nexus.erp.so.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售订单对象 so_sales_order_header
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_sales_order_header")
public class SoSalesOrderHeader extends TenantEntity {

    /** 销售订单ID */
    @TableId(value = "so_id")
    private Long soId;

    /** 销售订单编号 */
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
    /** 销售部门ID（关联sys_dept） */
    private Long deptId;
    /** 销售部门名称 */
    private String deptName;
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

    /** 销售订单行列表（非数据库字段） */
    @TableField(exist = false)
    private List<SoSalesOrderLine> soSalesOrderLineList;

    /** 删除标志 */
    @TableLogic
    private String delFlag;

    /** 审批流程ID */
    private Long selectFlowId;
}
