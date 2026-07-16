package com.nexus.erp.pu.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 采购订单实体对象 pu_purchase_order
 * <p>
 * 存储采购订单的主表信息，包括订单头信息、供应商信息、金额汇总、支付与交货条款等。
 * 采购订单是采购管理的核心单据，由采购申请转单或直接创建，
 * 经提交、审批后用于向供应商正式下达采购需求，后续可联动生成到货通知和收货单。
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pu_purchase_order")
public class PuPurchaseOrder extends BaseEntity {

    /** 采购订单ID，主键 */
    @TableId(value = "order_id")
    private Long orderId;

    /** 采购订单编号，系统自动生成，如 PU202605230001 */
    private String orderNo;

    /** 供应商ID，关联bd_vendor表 */
    private Long vendorId;

    /** 供应商编码 */
    private String vendorCode;

    /** 供应商名称 */
    private String vendorName;

    /** 采购部门ID（关联sys_dept） */
    private Long deptId;

    /** 采购部门名称 */
    private String deptName;

    /** 采购员ID（关联sys_user） */
    private Long buyerId;

    /** 采购员名称 */
    private String buyerName;

    /** 来源请购单ID，关联pu_purchase_requisition表 */
    private Long requisitionId;

    /** 来源请购单号 */
    private String requisitionNo;

    /** 订单日期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date orderDate;

    /** 预计到货日期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expectedArrivalDate;

    /**
     * 订单状态
     * <ul>
     *   <li>DRAFT - 草稿</li>
     *   <li>SUBMITTED - 已提交</li>
     *   <li>APPROVING - 审批中</li>
     *   <li>APPROVED - 已批准</li>
     *   <li>PARTIALLY_RECEIVED - 部分到货</li>
     *   <li>RECEIVED - 已全部到货</li>
     *   <li>CLOSED - 已关闭</li>
     *   <li>CANCELLED - 已作废</li>
     * </ul>
     */
    private String status;

    /** 币种，如 CNY、USD */
    private String currency;

    /** 汇率，相对于本币的换算比率 */
    private BigDecimal exchangeRate;

    /** 订单总金额（含税） */
    private BigDecimal totalAmount;

    /** 不含税总金额 */
    private BigDecimal amountExcludingTax;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 折扣金额 */
    private BigDecimal discountAmount;

    /** 运费金额 */
    private BigDecimal freightAmount;

    /** 付款条款 */
    private String paymentTerms;

    /** 交货条款 */
    private String deliveryTerms;

    /** 备注 */
    private String remark;

    /** 删除标志（0正常 1已删除） */
    private String delFlag;

    /** 选用的审批流ID，关联wf_custom_form表 */
    private Long selectFlowId;

    /** 合同ID，关联pu_contract表（预留字段，待合同模块开发后使用） */
    private Long contractId;

    /** 合同编号（预留字段，待合同模块开发后使用） */
    private String contractNo;
}
