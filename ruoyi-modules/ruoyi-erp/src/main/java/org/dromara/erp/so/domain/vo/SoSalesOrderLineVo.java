package org.dromara.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import org.dromara.erp.so.domain.SoSalesOrderLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 销售订单行视图对象 so_sales_order_line
 *
 * @author ruoyi
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SoSalesOrderLine.class)
public class SoSalesOrderLineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long lineId;

    @ExcelProperty(value = "销售订单ID")
    private Long soId;

    @ExcelProperty(value = "行号")
    private String lineNo;

    @ExcelProperty(value = "物料ID")
    private Long materialId;

    @ExcelProperty(value = "物料编码")
    private String materialCode;

    @ExcelProperty(value = "物料名称")
    private String materialName;

    @ExcelProperty(value = "物料规格")
    private String materialSpec;

    @ExcelProperty(value = "订单数量")
    private BigDecimal orderedQty;

    @ExcelProperty(value = "已发货数量")
    private BigDecimal deliveredQty;

    @ExcelProperty(value = "单位")
    private String unit;

    @ExcelProperty(value = "单价")
    private BigDecimal unitPrice;

    @ExcelProperty(value = "行金额")
    private BigDecimal lineAmount;

    @ExcelProperty(value = "税率")
    private BigDecimal taxRate;

    @ExcelProperty(value = "行税额")
    private BigDecimal taxAmount;

    @ExcelProperty(value = "行折扣率")
    private BigDecimal discountPercent;

    @ExcelProperty(value = "行折扣额")
    private BigDecimal discountAmount;

    @ExcelProperty(value = "行要求交期")
    private Date requestedDate;

    @ExcelProperty(value = "行承诺交期")
    private Date promiseDate;

    @ExcelProperty(value = "已履行数量")
    private BigDecimal fulfilledQty;

    @ExcelProperty(value = "已投产数量")
    private BigDecimal productionQty;

    @ExcelProperty(value = "已采购数量")
    private BigDecimal purchaseQty;

    @ExcelProperty(value = "已预留库存数量")
    private BigDecimal reservedQty;

    @ExcelProperty(value = "行状态")
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
    private String remark;
}
