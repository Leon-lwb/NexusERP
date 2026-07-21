package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 盘点单行对象 wm_stock_taking_line
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_stock_taking_line")
public class WmStockTakingLine extends TenantEntity {

    /**
     * 行ID
     */
    @TableId(value = "line_id")
    private Long lineId;

    /**
     * 盘点计划ID
     */
    private Long planId;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 物料规格
     */
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
     * 库存ID
     */
    private Long stockId;

    /**
     * 账面数量
     */
    private BigDecimal bookQuantity;

    /**
     * 实盘数量
     */
    private BigDecimal actualQuantity;

    /**
     * 差异数量
     */
    private BigDecimal differenceQuantity;

    /**
     * 状态
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}
