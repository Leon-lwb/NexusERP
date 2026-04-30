package org.dromara.erp.so.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.so.domain.SoSalesOrderLine;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 销售订单行业务对象 so_sales_order_line
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SoSalesOrderLine.class, reverseConvertGenerate = false)
public class SoSalesOrderLineBo extends BaseEntity {

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

    private Long[] ids;
}
