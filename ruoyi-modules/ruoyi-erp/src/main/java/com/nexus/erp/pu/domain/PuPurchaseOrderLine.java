package com.nexus.erp.pu.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;

/**
 * 采购订单明细实体对象 pu_purchase_order_line
 * <p>
 * 存储采购订单的物料明细行，每一行记录一个物料的采购数量、单价、税率及金额等信息。
 * 一个采购订单可包含多条明细，分别对应不同的采购物料。
 * 明细行可由采购申请明细转单生成，并记录已收货数量用于后续收货反馈。
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pu_purchase_order_line")
public class PuPurchaseOrderLine extends BaseEntity {

    /** 明细行ID，主键 */
    @TableId(value = "line_id")
    private Long lineId;

    /** 所属采购订单ID，关联pu_purchase_order表 */
    private Long orderId;

    /** 来源请购明细行ID，关联pu_purchase_requisition_line表 */
    private Long requisitionLineId;

    /** 物料ID，关联bd_material表 */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 物料规格型号 */
    private String materialSpec;

    /** 计量单位 */
    private String unit;

    /** 订购数量 */
    private BigDecimal orderQuantity;

    /** 已收货数量 */
    private BigDecimal receivedQuantity;

    /** 已开票数量(防重复开票) */
    private BigDecimal invoicedQty;

    /**
     * 已退货数量（采购退货执行后按订单行累加）。
     * <p>净采购量口径 = order_quantity - returned_quantity；可开票口径 = received_quantity - returned_quantity。
     * 对应列 {@code pu_purchase_order_line.returned_quantity}，新装定义见
     * {@code script/sql/ry-erp.sql}；存量库升级需单独审核。</p>
     */
    private BigDecimal returnedQuantity;

    /** 收货状态(PENDING/FULL_RECEIVED/PARTIAL_RECEIVED/OVER_RECEIVED/SHORT_RECEIVED) */
    private String receiveStatus;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 不含税单价 */
    private BigDecimal unitPriceExcludingTax;

    /** 税率，如 0.13 表示 13% */
    private BigDecimal taxRate;

    /** 行金额（含税），通常由数量×单价×（1+税率）计算得出 */
    private BigDecimal lineAmount;

    /** 不含税行金额 */
    private BigDecimal lineAmountExcludingTax;

    /** 行税额 */
    private BigDecimal lineTaxAmount;

    /** 排序号 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

    /** 删除标志（0正常 1已删除） */
    private String delFlag;
}
