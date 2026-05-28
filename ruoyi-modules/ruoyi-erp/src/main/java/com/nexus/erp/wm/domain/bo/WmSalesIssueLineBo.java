package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmSalesIssueLine;

import java.math.BigDecimal;

/**
 * 销售出库明细业务对象 wm_sales_issue_line
 *
 * @author ruoyi
 * @date 2026-04-03
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmSalesIssueLine.class, reverseConvertGenerate = false)
public class WmSalesIssueLineBo extends BaseEntity {

    /**
     * 出库单行ID
     */
    private Long lineId;

    /**
     * 出库单ID
     */
    private Long issueId;

    /**
     * 来源单据行ID（销售订单行ID）
     */
    private Long sourceDocLineId;

    /**
     * 行号
     */
    private String lineNo;

    /**
     * 产品物料ID
     */
    private Long materialId;

    /**
     * 产品物料编码
     */
    private String materialCode;

    /**
     * 产品物料名称
     */
    private String materialName;

    /**
     * 规格型号
     */
    private String materialSpec;

    /**
     * 单位ID
     */
    private String baseUomId;

    /**
     * 单位名称
     */
    private String baseUomName;

    /**
     * 批次ID
     */
    private Long batchId;

    /**
     * 批次号
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
     * 出库数量
     */
    private BigDecimal quantityIssue;

    /**
     * 订单数量（冗余）
     */
    private BigDecimal quantityOrdered;

    /**
     * 已交付数量（冗余）
     */
    private BigDecimal quantityDelivered;

    /**
     * 单价
     */
    private BigDecimal unitPrice;

    /**
     * 行金额
     */
    private BigDecimal lineAmount;

    /**
     * 备注
     */
    private String remark;
}
