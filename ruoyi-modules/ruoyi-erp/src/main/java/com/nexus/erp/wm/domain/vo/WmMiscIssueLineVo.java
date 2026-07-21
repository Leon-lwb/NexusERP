package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmMiscIssueLine;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 杂项出库单行视图对象 wm_misc_issue_line
 *
 * @author nexus
 */
@Data
@AutoMapper(target = WmMiscIssueLine.class)
public class WmMiscIssueLineVo implements Serializable {

    /**
     * 行ID
     */
    private Long lineId;

    /**
     * 出库单ID
     */
    private Long issueId;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码
     */
    @ExcelProperty("物料编码")
    private String materialCode;

    /**
     * 物料名称
     */
    @ExcelProperty("物料名称")
    private String materialName;

    /**
     * 物料规格
     */
    @ExcelProperty("物料规格")
    private String materialSpec;

    /**
     * 基本单位ID
     */
    private String baseUomId;

    /**
     * 基本单位名称
     */
    private String baseUomName;

    /**
     * 批次ID
     */
    private Long batchId;

    /**
     * 批次编码
     */
    private String batchCode;

    /**
     * 库位ID
     */
    private Long locationId;

    /**
     * 库位编码
     */
    private String locationCode;

    /**
     * 库位名称
     */
    private String locationName;

    /**
     * 数量
     */
    @ExcelProperty("数量")
    private BigDecimal quantity;

    /**
     * 单价
     */
    @ExcelProperty("单价")
    private BigDecimal unitPrice;

    /**
     * 金额
     */
    @ExcelProperty("金额")
    private BigDecimal amount;

    /**
     * 备注
     */
    private String remark;
}
