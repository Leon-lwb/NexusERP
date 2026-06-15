package com.nexus.erp.pu.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.pu.domain.PuPurchaseOrder;
import com.nexus.erp.pu.domain.PuPurchaseOrderLine;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 采购订单业务对象 BO
 * <p>
 * 用于采购订单的前后端数据交互，承载新增、修改、查询等操作的请求参数。
 * 与 {@link PuPurchaseOrder} 实体通过 MapStruct 自动映射转换。
 * 包含订单头信息和明细行列表 {@link #lineList}，前端提交时批量传输。
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PuPurchaseOrder.class)
public class PuPurchaseOrderBo extends BaseEntity {

    @NotNull(message = "采购订单ID不能为空")
    private Long orderId;

    @NotBlank(message = "采购订单号不能为空")
    private String orderNo;

    /** 供应商ID */
    private Long vendorId;

    /** 供应商编码 */
    private String vendorCode;

    /** 供应商名称 */
    private String vendorName;

    /** 来源请购单ID */
    private Long requisitionId;

    /** 来源请购单号 */
    private String requisitionNo;

    /** 订单日期 */
    private Date orderDate;

    /** 预计到货日期 */
    private Date expectedArrivalDate;

    /** 订单状态 */
    private String status;

    /** 币种 */
    private String currency;

    /** 汇率 */
    private BigDecimal exchangeRate;

    /** 订单总金额 */
    private BigDecimal totalAmount;

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

    /** 采购订单明细行列表 */
    private List<PuPurchaseOrderLine> lineList;

    /** 合同ID（预留字段，待合同模块开发后使用） */
    private Long contractId;

    /** 合同编号（预留字段，待合同模块开发后使用） */
    private String contractNo;
}
