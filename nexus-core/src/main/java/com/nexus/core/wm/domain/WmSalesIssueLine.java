package com.nexus.core.wm.domain;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 销售出库明细对象 wm_sales_issue_line
 *
 * @author nexus
 * @date 2026-04-03
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.wm.mapper.WmSalesIssueLineMapper.WmSalesIssueLineResult")
public class WmSalesIssueLine extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 出库单行ID
     */
    @TableId(value = "line_id", type = IdType.AUTO)
    private Long lineId;

    /**
     * 出库单ID
     */
    @Excel(name = "出库单ID")
    private Long issueId;

    /**
     * 来源单据行ID（销售订单行ID）
     */
    @Excel(name = "来源单据行ID", readConverterExp = "销=售订单行ID")
    private Long sourceDocLineId;

    /**
     * 行号
     */
    @Excel(name = "行号")
    private String lineNo;

    /**
     * 产品物料ID
     */
    @Excel(name = "产品物料ID")
    private Long materialId;

    /**
     * 产品物料编码
     */
    @Excel(name = "产品物料编码")
    private String materialCode;

    /**
     * 产品物料名称
     */
    @Excel(name = "产品物料名称")
    private String materialName;

    /**
     * 规格型号
     */
    @Excel(name = "规格型号")
    private String materialSpec;

    /**
     * 单位ID
     */
    @Excel(name = "单位ID")
    private String baseUomId;

    /**
     * 单位名称
     */
    @Excel(name = "单位名称")
    private String baseUomName;

    /**
     * 批次ID
     */
    @Excel(name = "批次ID")
    private Long batchId;

    /**
     * 批次号
     */
    @Excel(name = "批次号")
    private String batchCode;

    /**
     * 库位ID
     */
    @Excel(name = "库位ID")
    private Long locationId;

    /**
     * 库位编码
     */
    @Excel(name = "库位编码")
    private String locationCode;

    /**
     * 库位名称
     */
    @Excel(name = "库位名称")
    private String locationName;

    /**
     * 出库数量
     */
    @Excel(name = "出库数量")
    private BigDecimal quantityIssue;

    /**
     * 订单数量（冗余）
     */
    @Excel(name = "订单数量", readConverterExp = "冗=余")
    private BigDecimal quantityOrdered;

    /**
     * 已交付数量（冗余）
     */
    @Excel(name = "已交付数量", readConverterExp = "冗=余")
    private BigDecimal quantityDelivered;

    /**
     * 单价
     */
    @Excel(name = "单价")
    private BigDecimal unitPrice;

    /**
     * 行金额
     */
    @Excel(name = "行金额")
    private BigDecimal lineAmount;

}