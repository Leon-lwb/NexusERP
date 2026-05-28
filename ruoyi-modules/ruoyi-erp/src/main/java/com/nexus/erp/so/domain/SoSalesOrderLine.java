package com.nexus.erp.so.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 销售订单行对象 so_sales_order_line
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_sales_order_line")
public class SoSalesOrderLine extends TenantEntity {

    @TableId(value = "line_id")
    private Long lineId;

    private Long soId;
    private String lineNo;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String materialSpec;
    private BigDecimal orderedQty;
    private BigDecimal deliveredQty;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal lineAmount;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal discountPercent;
    private BigDecimal discountAmount;
    private Date requestedDate;
    private Date promiseDate;
    private BigDecimal fulfilledQty;
    private BigDecimal productionQty;
    private BigDecimal purchaseQty;
    private BigDecimal reservedQty;
    private String lineStatus;
    private Long linkedMoId;
    private Long linkedPoId;
    private String reservedBatchNo;
    private String reservedSerials;
    private Long paGroupId;
    private Long warehouseId;
    private Long deliveryPriority;
    private String customerLineRef;
    private Long sortOrder;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;
    private String remark;

    @TableLogic
    private String delFlag;
}
