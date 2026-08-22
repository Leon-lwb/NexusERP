package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoSalesOrderLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 销售订单行视图对象 so_sales_order_line
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SoSalesOrderLine.class)
public class SoSalesOrderLineVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 行ID */
    private Long lineId;

    /** 销售订单ID */
    @ExcelProperty(value = "销售订单ID")
    private Long soId;

    /** 销售订单编号 */
    @ExcelProperty(value = "销售订单编号")
    private String soNo;

    /** 行号 */
    @ExcelProperty(value = "行号")
    private String lineNo;

    /** 物料ID */
    @ExcelProperty(value = "物料ID")
    private Long materialId;

    /** 物料编码 */
    @ExcelProperty(value = "物料编码")
    private String materialCode;

    /** 物料名称 */
    @ExcelProperty(value = "物料名称")
    private String materialName;

    /** 物料规格 */
    @ExcelProperty(value = "物料规格")
    private String materialSpec;

    /** 订单数量 */
    @ExcelProperty(value = "订单数量")
    private BigDecimal orderedQty;

    /** 已发货数量 */
    @ExcelProperty(value = "已发货数量")
    private BigDecimal deliveredQty;

    /** 已开票数量(防重复开票) */
    @ExcelProperty(value = "已开票数量")
    private BigDecimal invoicedQty;

    /** 单位 */
    @ExcelProperty(value = "单位")
    private String unit;

    /** 单价 */
    @ExcelProperty(value = "单价")
    private BigDecimal unitPrice;

    /** 行金额 */
    @ExcelProperty(value = "行金额")
    private BigDecimal lineAmount;

    /** 税率 */
    @ExcelProperty(value = "税率")
    private BigDecimal taxRate;

    /** 行税额 */
    @ExcelProperty(value = "行税额")
    private BigDecimal taxAmount;

    /** 行折扣率 */
    @ExcelProperty(value = "行折扣率")
    private BigDecimal discountPercent;

    /** 行折扣额 */
    @ExcelProperty(value = "行折扣额")
    private BigDecimal discountAmount;

    /** 行要求交期 */
    @ExcelProperty(value = "行要求交期")
    private Date requestedDate;

    /** 行承诺交期 */
    @ExcelProperty(value = "行承诺交期")
    private Date promiseDate;

    /** 已履行数量 */
    @ExcelProperty(value = "已履行数量")
    private BigDecimal fulfilledQty;

    /** 已投产数量 */
    @ExcelProperty(value = "已投产数量")
    private BigDecimal productionQty;

    /** 已采购数量 */
    @ExcelProperty(value = "已采购数量")
    private BigDecimal purchaseQty;

    /** 已预留库存数量 */
    @ExcelProperty(value = "已预留库存数量")
    private BigDecimal reservedQty;

    /** 行状态 */
    @ExcelProperty(value = "行状态")
    private String lineStatus;

    /** 关联生产订单ID */
    private Long linkedMoId;
    /** 关联生产工单号（多工单逗号分隔，展示用，selectById 经计划链回填） */
    private String linkedWoNo;
    /** 关联生产工单ID（首张，展示跳转用，selectById 经计划链回填，不落库） */
    private Long linkedWoId;
    /** 关联采购订单ID */
    private Long linkedPoId;
    /** 预留批次号 */
    private String reservedBatchNo;
    /** 预留序列号 */
    private String reservedSerials;
    /** 配套组ID */
    private Long paGroupId;
    /** 仓库ID */
    private Long warehouseId;
    /** 仓库名称（展示用，selectById 经 warehouseId 回填） */
    private String warehouseName;
    /** 交货优先级 */
    private Long deliveryPriority;
    /** 客户行参考 */
    private String customerLineRef;
    /** 客户产品代号(用于MES发布包精确匹配) */
    private String customerProductCode;
    /** 客户产品名称 */
    private String customerProductName;
    /** 排序号 */
    private Long sortOrder;
    /** 备注 */
    private String remark;

    /** 关联生产计划编号 */
    @ExcelProperty(value = "关联生产计划编号")
    private String linkedPlanNo;

    /** 关联生产计划ID */
    private Long linkedPlanId;

    /** OQC检验状态(PENDING/QUALIFIED/UNQUALIFIED) */
    @ExcelProperty(value = "OQC检验状态")
    private String oqcStatus;
    /** OQC检验单ID */
    private Long oqcId;
    /** OQC检验单编号 */
    @ExcelProperty(value = "OQC检验单编号")
    private String oqcCode;
    /** OQC检验时间 */
    private Date oqcTime;
}
