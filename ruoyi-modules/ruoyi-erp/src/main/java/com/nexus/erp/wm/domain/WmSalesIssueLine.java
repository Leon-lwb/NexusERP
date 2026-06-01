package com.nexus.erp.wm.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 销售出库明细对象 wm_sales_issue_line
 *
 * @author nexus
 * @date 2026-04-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("wm_sales_issue_line")
public class WmSalesIssueLine extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 出库单行ID
     */
    @TableId(value = "line_id")
    private Long lineId;

    /**
     * 出库单ID
     */
    @ExcelProperty("出库单ID")
    private Long issueId;

    /**
     * 来源单据行ID（销售订单行ID）
     */
    @ExcelProperty(value = "来源单据行ID")
    private Long sourceDocLineId;

    /**
     * 行号
     */
    @ExcelProperty("行号")
    private String lineNo;

    /**
     * 产品物料ID
     */
    @ExcelProperty("产品物料ID")
    private Long materialId;

    /**
     * 产品物料编码
     */
    @ExcelProperty("产品物料编码")
    private String materialCode;

    /**
     * 产品物料名称
     */
    @ExcelProperty("产品物料名称")
    private String materialName;

    /**
     * 规格型号
     */
    @ExcelProperty("规格型号")
    private String materialSpec;

    /**
     * 单位ID
     */
    @ExcelProperty("单位ID")
    private String baseUomId;

    /**
     * 单位名称
     */
    @ExcelProperty("单位名称")
    private String baseUomName;

    /**
     * 批次ID
     */
    @ExcelProperty("批次ID")
    private Long batchId;

    /**
     * 批次号
     */
    @ExcelProperty("批次号")
    private String batchCode;

    /**
     * 库位ID
     */
    @ExcelProperty("库位ID")
    private Long locationId;

    /**
     * 库位编码
     */
    @ExcelProperty("库位编码")
    private String locationCode;

    /**
     * 库位名称
     */
    @ExcelProperty("库位名称")
    private String locationName;

    /**
     * 出库数量
     */
    @ExcelProperty("出库数量")
    private BigDecimal quantityIssue;

    /**
     * 订单数量（冗余）
     */
    @ExcelProperty("订单数量")
    private BigDecimal quantityOrdered;

    /**
     * 已交付数量（冗余）
     */
    @ExcelProperty("已交付数量")
    private BigDecimal quantityDelivered;

    /**
     * 单价
     */
    @ExcelProperty("单价")
    private BigDecimal unitPrice;

    /**
     * 行金额
     */
    @ExcelProperty("行金额")
    private BigDecimal lineAmount;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;
}
