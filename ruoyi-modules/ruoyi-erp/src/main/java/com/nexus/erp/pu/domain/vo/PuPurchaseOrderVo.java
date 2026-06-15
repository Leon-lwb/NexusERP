package com.nexus.erp.pu.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 采购订单视图对象 VO
 * <p>
 * 用于采购订单的前端展示和导出，通过 {@link ExcelProperty} 注解定义导出列映射。
 * 与 {@link PuPurchaseOrder} 实体通过 MapStruct 自动映射转换。
 * 包含订单头展示信息和明细行列表 {@link #lineList}。
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PuPurchaseOrder.class)
public class PuPurchaseOrderVo extends BaseEntity {

    /** 采购订单ID */
    private Long orderId;

    @ExcelProperty(value = "采购订单号")
    private String orderNo;

    @ExcelProperty(value = "供应商")
    private String vendorName;

    /** 供应商ID */
    private Long vendorId;

    @ExcelProperty(value = "来源请购单号")
    private String requisitionNo;

    /** 来源请购单ID */
    private Long requisitionId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "订单日期")
    private Date orderDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @ExcelProperty(value = "预计到货日期")
    private Date expectedArrivalDate;

    @ExcelProperty(value = "状态")
    private String status;

    @ExcelProperty(value = "金额")
    private BigDecimal totalAmount;

    /** 币种 */
    private String currency;

    /** 汇率 */
    private BigDecimal exchangeRate;

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

    /** 选用的审批流ID */
    private Long selectFlowId;

    /** 备注 */
    private String remark;

    /** 采购订单明细行列表 */
    private List<PuPurchaseOrderLine> lineList;

    /** 合同ID（预留字段，待合同模块开发后使用） */
    private Long contractId;

    /** 合同编号（预留字段，待合同模块开发后使用） */
    @ExcelProperty(value = "合同编号")
    private String contractNo;
}
